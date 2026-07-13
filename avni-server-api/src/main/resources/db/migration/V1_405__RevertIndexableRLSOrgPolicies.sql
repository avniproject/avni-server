-- Revert indexable RLS org policies (avniproject/avni-server#1029) -- hotfix on 17.0.
--
-- WHY
--   V1_398 (#1008) rewrote every org RLS policy USING clause to `organisation_id = ANY(rls_visible_org_ids())`
--   (and the *_with_ancestors variant for ref tables). The STABLE function is NOT hoisted to a once-per-query
--   InitPlan inside `= ANY(fn())`; the planner re-evaluates it PER INDEX TUPLE. On prod this collapsed every
--   org-scoped query: /v2/syncDetails went from ~2s to minutes and worsened as load built, exhausting the
--   connection pool. Verified on prod: an identical trivial query was 35ms with the policy off vs 1,573ms with
--   it on, purely from the per-tuple re-evaluation; and `individual` (which HAS an organisation_id index) still
--   uses the per-row form, so index-only fixes (V1_404) relieve but do not resolve it for large orgs.
--
-- WHAT
--   Restore the pre-#1008 (V1_142.2) `enable_rls_on_tx_table` / `enable_rls_on_ref_table` bodies -- the
--   `organisation_id = (scalar subquery) OR organisation_id IN (hashed subplan)` USING clause, which the planner
--   evaluates ONCE per query -- and re-apply them to every *_orgs policy still on the indexable shape. This is
--   the proven pre-17.0 behaviour (~2s syncDetails baseline). WITH CHECK is unchanged; visibility is preserved
--   exactly (own org + org-group members for tx; own org + ancestors + org-group members for ref).
--
--   The proper re-do -- keep the index plan AND evaluate the org set once, via
--   `organisation_id IN (SELECT unnest(rls_visible_org_ids()))` plus the V1_404 indexes -- is #1030 (17.0.1).
--   This migration intentionally LEAVES the rls_visible_org_ids* functions and V1_404's indexes in place: the
--   functions are unused by the reverted policy but harmless, and #1030 reuses both.
--
-- SAFETY
--   Catalog-only: no data scans, no index builds -- runs in seconds. Each policy swap takes a brief
--   ACCESS EXCLUSIVE lock on its table; SET LOCAL lock_timeout makes a blocked lock fail fast (atomic rollback
--   to the current policies) instead of queueing the cluster. Run in the normal deploy window (app stopped, no
--   direct-DB ETL/analytics) so the per-table lock is not held off by in-flight long syncDetails queries.

SET LOCAL lock_timeout = '10s';

-- 1. Restore the pre-#1008 helper bodies verbatim from V1_142.2 (standalone, OR-based USING, once-per-query).
--    CREATE OR REPLACE keeps the existing signature that V1_398 redefined; the V1_398 delegating bodies (and
--    enable_rls_on_org_table) are simply overwritten here.

CREATE OR REPLACE FUNCTION enable_rls_on_ref_table(tablename text) RETURNS text
    LANGUAGE plpgsql
AS
$$
DECLARE
    tabl   TEXT := quote_ident(tablename);
    polisy TEXT := quote_ident(tablename || '_orgs') || ' ON ' || tabl || ' ';
BEGIN
    EXECUTE 'DROP POLICY IF EXISTS ' || polisy;
    EXECUTE 'CREATE POLICY ' || polisy || '
            USING (organisation_id IN (SELECT id FROM org_ids UNION SELECT organisation_id from organisation_group_organisation)
            OR organisation_id IN (SELECT organisation_id from organisation_group_organisation))
  WITH CHECK ((organisation_id = (select id
                                  from organisation
                                  where db_user = current_user)))';
    EXECUTE 'ALTER TABLE ' || tabl || ' ENABLE ROW LEVEL SECURITY';
    RETURN 'CREATED POLICY ' || polisy;
END
$$;
ALTER FUNCTION enable_rls_on_ref_table(text) OWNER TO openchs;

CREATE OR REPLACE FUNCTION enable_rls_on_tx_table(tablename text) RETURNS text
    LANGUAGE plpgsql
AS
$$
DECLARE
    tabl   TEXT := quote_ident(tablename);
    polisy TEXT := quote_ident(tablename || '_orgs') || ' ON ' || tabl || ' ';
BEGIN
    EXECUTE 'DROP POLICY IF EXISTS ' || polisy;
    EXECUTE 'CREATE POLICY ' || polisy || '
            USING ((organisation_id = (select id from organisation where db_user = current_user)
            OR organisation_id IN (SELECT organisation_id from organisation_group_organisation)))
    WITH CHECK ((organisation_id = (select id from organisation where db_user = current_user)))';
    EXECUTE 'ALTER TABLE ' || tabl || ' ENABLE ROW LEVEL SECURITY';
    RETURN 'CREATED POLICY ' || polisy;
END
$$;
ALTER FUNCTION enable_rls_on_tx_table(text) OWNER TO openchs;

-- 2. Re-apply the matching helper to every *_orgs policy still on the #1008 indexable shape, driven by the live
--    catalog (the authoritative source). Classification is strict and unambiguous:
--      ref = USING references rls_visible_org_ids_with_ancestors();
--      tx  = USING references rls_visible_org_ids() and NOT the _with_ancestors variant.
--    (rls_visible_org_ids is a substring of rls_visible_org_ids_with_ancestors, so ref MUST be tested first.)
--    A policy matching neither is 'unknown' and ABORTS -- never silently mis-applied (a tx table wrongly treated
--    as ref would widen visibility to ancestor orgs = cross-tenant). The `qual LIKE '%rls_visible_org_ids%'`
--    selection filter makes the whole step idempotent: once reverted, policies no longer match, so a re-run is a
--    no-op. pg_temp.v1_405_indexable_orgs_policies() is the SINGLE source of truth shared by the loop and the
--    trailing assertion, so the selection predicate cannot drift between them.

CREATE OR REPLACE FUNCTION pg_temp.v1_405_indexable_orgs_policies()
    RETURNS TABLE (table_name text, policy_name text, kind text)
    LANGUAGE sql
AS
$$
SELECT table_name,
       policy_name,
       CASE
           WHEN qual LIKE '%rls_visible_org_ids_with_ancestors%' THEN 'ref'
           WHEN qual LIKE '%rls_visible_org_ids%' THEN 'tx'
           ELSE 'unknown'
       END AS kind
FROM (SELECT c.relname::text                    AS table_name,
             p.polname::text                    AS policy_name,
             pg_get_expr(p.polqual, p.polrelid) AS qual
      FROM pg_policy p
               JOIN pg_class c ON c.oid = p.polrelid
               JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND p.polname LIKE '%\_orgs') candidate
WHERE qual LIKE '%rls_visible_org_ids%';
$$;

DO
$$
    DECLARE
        rec      record;
        unknowns text;
        reverted int := 0;
    BEGIN
        SELECT string_agg(policy_name || ' on ' || table_name, ', ')
        INTO unknowns
        FROM pg_temp.v1_405_indexable_orgs_policies()
        WHERE kind = 'unknown';
        IF unknowns IS NOT NULL THEN
            RAISE EXCEPTION 'V1_405: cannot classify indexable org-scoped policy(ies) as tx or ref: %', unknowns;
        END IF;

        FOR rec IN SELECT * FROM pg_temp.v1_405_indexable_orgs_policies()
            LOOP
                -- Drop here only when the policy name is non-canonical (a renamed table whose policy kept the old
                -- name, e.g. manual_message carrying manual_broadcast_message_orgs); otherwise the helper's own
                -- DROP POLICY IF EXISTS removes the canonical <table>_orgs.
                IF rec.policy_name <> rec.table_name || '_orgs' THEN
                    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', rec.policy_name, rec.table_name);
                END IF;
                IF rec.kind = 'ref' THEN
                    PERFORM public.enable_rls_on_ref_table(rec.table_name);
                ELSE
                    PERFORM public.enable_rls_on_tx_table(rec.table_name);
                END IF;
                reverted := reverted + 1;
                RAISE NOTICE 'V1_405: reverted % policy on % to the pre-17.0 OR-based shape', rec.kind, rec.table_name;
            END LOOP;
        RAISE NOTICE 'V1_405: reverted % org-scoped policy(ies) to the pre-17.0 shape', reverted;

        -- Trailing assertion (same source of truth): fail the migration (atomic rollback) if any org-scoped
        -- policy is still on the #1008 indexable shape.
        IF EXISTS (SELECT 1 FROM pg_temp.v1_405_indexable_orgs_policies()) THEN
            RAISE EXCEPTION 'V1_405: % org-scoped policy(ies) still reference rls_visible_org_ids',
                (SELECT count(*) FROM pg_temp.v1_405_indexable_orgs_policies());
        END IF;
    END
$$;

DROP FUNCTION pg_temp.v1_405_indexable_orgs_policies();
