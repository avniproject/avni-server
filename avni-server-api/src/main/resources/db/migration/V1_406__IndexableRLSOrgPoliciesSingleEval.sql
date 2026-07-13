-- avniproject/avni-server#1030 | Re-do indexable RLS with SINGLE evaluation of rls_visible_org_ids() (17.0.1).
--
-- WHY
--   #1008 (V1_398) made org policies `organisation_id = ANY(rls_visible_org_ids())`. The STABLE function is NOT
--   hoisted to a once-per-query InitPlan inside `= ANY(fn())` -- Postgres re-evaluates it PER INDEX TUPLE, so
--   every org-scoped query cost seconds-minutes and exhausted the pool (prod: an identical trivial group_privilege
--   query was 35ms policy-off vs 1,573ms policy-on). #1029 (V1_405) reverted to the pre-17.0 OR-based USING, which
--   evaluates once per query but seq-scans -- the May-2026 latency #1008 set out to fix. This restores the index
--   plan WITHOUT the per-tuple cost.
--
-- WHAT -- and why this exact spelling
--   USING becomes:  organisation_id = ANY (COALESCE((SELECT <fn>()), '{}'::integer[]))
--   The scalar sublink `(SELECT fn())` becomes an uncorrelated InitPlan: evaluated ONCE per query, its integer[]
--   result bound as a Param. `= ANY (COALESCE($0, '{}'))` is then an ordinary indexable ScalarArrayOpExpr over a
--   pseudoconstant, so the plan is `InitPlan 1 (returns $0)` +
--   `Index (Only) Scan .. Index Cond: (organisation_id = ANY (COALESCE($0, ...)))`. Verified through a real policy
--   under SET ROLE, including UPDATE/DELETE-injected quals (InitPlan there too).
--
--   Every wrapper here is doing a job -- do not "simplify" either away:
--   * The `(SELECT ...)` is what makes the function evaluate once: a bare `= ANY (fn())` keeps the STABLE function
--     inline in the qual, re-evaluated per tuple (the #1008 collapse). Removing the COALESCE but keeping the
--     sublink fails loudly (`= ANY ((SELECT fn()))` is an ANY-sublink: integer = integer[] type error).
--   * The COALESCE is the fail-closed guard: if the scalar subquery ever yielded NULL, `= ANY(NULL)` would hide
--     every row silently; the explicit '{}' fallback states that intent (and mirrors the same coalesce inside the
--     function body).
--   The functions must stay STABLE and never be made IMMUTABLE: immutable would let the planner constant-fold the
--   visible-org set into cached/generic plans, leaking one role's org set into another session's plan.
--   IndexableRlsPolicyMigrationIntegrationTest pins the catalog shape and RlsPolicyPlanShapeIntegrationTest pins
--   the through-policy plan, so any respelling that loses the InitPlan fails CI.
--
--   Why not #1030's originally-specced `organisation_id IN (SELECT unnest(fn()))`: that shape index-serves ONLY
--   when written inline in a query's WHERE clause. As a POLICY qual it lives in the relation's securityQuals, which
--   the planner never sublink-pulls-up into a join -- it stays a per-row `Filter: (hashed SubPlan)` on a seq scan
--   (function still evaluated once, so it is safe -- but the plan and timing are identical to the V1_405 revert,
--   i.e. a no-op). Verified empirically through a real policy; the spec's plan evidence was measured inline, where
--   pull-up applies. This is also why plan validation for RLS work MUST go through an actual policy under SET ROLE.
--
--   WITH CHECK is unchanged (own org only). Visibility is preserved exactly: tx = own org + org-group members;
--   ref = own org + ancestors + org-group members (via the retained rls_visible_org_ids* functions, re-asserted
--   below unchanged so this migration is self-contained). Reuses V1_404's organisation_id btrees (the SAOP index
--   seeks need them) and adds the one still missing on an AC-named hot table: group_privilege.
--
-- SEQUENCING (critical)
--   Deploy ONLY after #1029 (V1_405) is confirmed on the target DB. This migration re-applies policies still on
--   the reverted OR-based shape; any policy left on the #1008 `= ANY(fn())` shape is NOT silently migrated -- the
--   trailing assertion aborts (atomic rollback) so a partially-reverted DB can never be re-collapsed.
--
-- SAFETY
--   Policy swaps are catalog-only, each taking a brief ACCESS EXCLUSIVE lock on its table. The group_privilege
--   index build takes a SHARE lock (blocks writes, not reads) and scans the table for the build's duration --
--   SET LOCAL lock_timeout bounds lock ACQUISITION, not build time. group_privilege is config-sized (rows per
--   group per privilege), so the build is short; on very large environments pre-create it with
--   CREATE INDEX CONCURRENTLY (same name -- IF NOT EXISTS then no-ops here). Run in the deploy window (app
--   stopped, no direct-DB ETL/analytics) so the per-table locks are not held off by in-flight queries.

SET LOCAL lock_timeout = '10s';

-- 0. The organisation_id btree the SAOP index seeks need on group_privilege (V1_404 covers the other hot tables).
CREATE INDEX IF NOT EXISTS group_privilege_org_id_idx ON group_privilege (organisation_id);

-- 1. Re-assert the visible-org-ids functions (unchanged from V1_398; retained by V1_405). STABLE + integer[].
CREATE OR REPLACE FUNCTION public.rls_visible_org_ids() RETURNS integer[]
    LANGUAGE sql
    STABLE
AS
$$
SELECT coalesce(array_agg(org_id), '{}'::integer[])
FROM (SELECT id AS org_id FROM public.organisation WHERE db_user = current_user
      UNION
      SELECT organisation_id FROM public.organisation_group_organisation) visible_orgs;
$$;
ALTER FUNCTION public.rls_visible_org_ids() OWNER TO openchs;

CREATE OR REPLACE FUNCTION public.rls_visible_org_ids_with_ancestors() RETURNS integer[]
    LANGUAGE sql
    STABLE
AS
$$
SELECT coalesce(array_agg(org_id), '{}'::integer[])
FROM (SELECT id AS org_id FROM public.org_ids
      UNION
      SELECT organisation_id FROM public.organisation_group_organisation) visible_orgs;
$$;
ALTER FUNCTION public.rls_visible_org_ids_with_ancestors() OWNER TO openchs;

-- 2. Re-point the policy helpers at the single-eval InitPlan USING clause. WITH CHECK unchanged.
--    Schema-qualified target (public.%I) so the policy is immune to the "$user" search_path trap (under
--    SET ROLE <org> an unqualified name resolves to the org's ETL schema, not public). visibleOrgIdsFn is a
--    trusted internal literal, not user input.
CREATE OR REPLACE FUNCTION enable_rls_on_org_table(tablename text, visibleOrgIdsFn text) RETURNS text
    LANGUAGE plpgsql
AS
$$
DECLARE
    tabl   TEXT := format('public.%I', tablename);
    polisy TEXT := quote_ident(tablename || '_orgs') || ' ON ' || tabl || ' ';
BEGIN
    EXECUTE 'DROP POLICY IF EXISTS ' || polisy;
    EXECUTE 'CREATE POLICY ' || polisy || '
            USING (organisation_id = ANY (COALESCE((SELECT ' || visibleOrgIdsFn || '()), ''{}''::integer[])))
            WITH CHECK ((organisation_id = (select id from public.organisation where db_user = current_user)))';
    EXECUTE 'ALTER TABLE ' || tabl || ' ENABLE ROW LEVEL SECURITY';
    RETURN 'CREATED POLICY ' || polisy;
END
$$;
ALTER FUNCTION enable_rls_on_org_table(text, text) OWNER TO openchs;

CREATE OR REPLACE FUNCTION enable_rls_on_ref_table(tablename text) RETURNS text
    LANGUAGE plpgsql
AS
$$
BEGIN
    RETURN enable_rls_on_org_table(tablename, 'public.rls_visible_org_ids_with_ancestors');
END
$$;
ALTER FUNCTION enable_rls_on_ref_table(text) OWNER TO openchs;

CREATE OR REPLACE FUNCTION enable_rls_on_tx_table(tablename text) RETURNS text
    LANGUAGE plpgsql
AS
$$
BEGIN
    RETURN enable_rls_on_org_table(tablename, 'public.rls_visible_org_ids');
END
$$;
ALTER FUNCTION enable_rls_on_tx_table(text) OWNER TO openchs;

-- 3. Re-apply the matching helper to every org-scoped (`*_orgs`) policy still on the reverted OR-based shape,
--    driven by the live catalog (authoritative -- migration history has renamed/flip-flopped tables).
--    Selection: policies whose USING does not reference rls_visible_org_ids at all -- i.e. exactly the reverted
--    OR-based policies (their deparsed qual references organisation.db_user / the org_ids view directly). A
--    NULL-qual policy coalesces to '' and is selected too, then aborts as 'unknown' -- fails closed, never skipped.
--    A lingering #1008 `= ANY(rls_visible_org_ids())` policy DOES reference the function, so it is NOT selected
--    here; the trailing assertion catches it (it lacks the COALESCE-wrapped sublink) and aborts.
--    Classification (reverted shapes are unambiguous): ref USING references the `org_ids` view and never db_user;
--    tx USING references organisation.db_user and never org_ids. Neither => 'unknown' => ABORT (a tx table wrongly
--    treated as ref would widen visibility to ancestors = cross-tenant). Single source of truth shared by the loop
--    and the unknown check so the predicate cannot drift.
CREATE OR REPLACE FUNCTION pg_temp.v1_406_orbased_orgs_policies()
    RETURNS TABLE (table_name text, policy_name text, kind text)
    LANGUAGE sql
AS
$$
SELECT table_name,
       policy_name,
       CASE
           WHEN qual LIKE '%org_ids%' AND qual NOT LIKE '%db_user%' THEN 'ref'
           WHEN qual LIKE '%db_user%' AND qual NOT LIKE '%org_ids%' THEN 'tx'
           ELSE 'unknown'
       END AS kind
FROM (SELECT c.relname::text                                 AS table_name,
             p.polname::text                                 AS policy_name,
             coalesce(pg_get_expr(p.polqual, p.polrelid), '') AS qual
      FROM pg_policy p
               JOIN pg_class c ON c.oid = p.polrelid
               JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND p.polname LIKE '%\_orgs') candidate
WHERE qual NOT LIKE '%rls_visible_org_ids%';
$$;

DO
$$
    DECLARE
        rec        record;
        unknowns   text;
        stragglers text;
        migrated   int := 0;
    BEGIN
        SELECT string_agg(policy_name || ' on ' || table_name, ', ')
        INTO unknowns
        FROM pg_temp.v1_406_orbased_orgs_policies()
        WHERE kind = 'unknown';
        IF unknowns IS NOT NULL THEN
            RAISE EXCEPTION 'V1_406: cannot classify org-scoped policy(ies) as tx or ref: %', unknowns;
        END IF;

        FOR rec IN SELECT * FROM pg_temp.v1_406_orbased_orgs_policies()
            LOOP
                -- Drop by real name only when non-canonical (renamed table keeping an old policy name); otherwise
                -- the helper's own DROP POLICY IF EXISTS removes the canonical <table>_orgs.
                IF rec.policy_name <> rec.table_name || '_orgs' THEN
                    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', rec.policy_name, rec.table_name);
                END IF;
                IF rec.kind = 'ref' THEN
                    PERFORM public.enable_rls_on_ref_table(rec.table_name);
                ELSE
                    PERFORM public.enable_rls_on_tx_table(rec.table_name);
                END IF;
                migrated := migrated + 1;
                RAISE NOTICE 'V1_406: re-applied % policy on % as = ANY (COALESCE((SELECT fn()), ''{}''))', rec.kind, rec.table_name;
            END LOOP;
        RAISE NOTICE 'V1_406: re-applied % org-scoped policy(ies) to the single-eval InitPlan shape', migrated;

        -- Trailing assertion: EVERY *_orgs policy must now be on the InitPlan shape -- its deparsed qual references
        -- rls_visible_org_ids AND carries the COALESCE wrapper. Fails closed on NULL quals (coalesce to ''), on a
        -- reverted policy the loop missed, AND on a lingering #1008 `= ANY(fn())` policy (has the function name but
        -- no COALESCE). Any straggler aborts the migration atomically. If this fires citing an ANY(fn()) policy,
        -- the DB was not fully reverted by #1029 (V1_405); apply that first.
        SELECT string_agg(p.polname || ' on ' || c.relname, ', ')
        INTO stragglers
        FROM pg_policy p
                 JOIN pg_class c ON c.oid = p.polrelid
                 JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND p.polname LIKE '%\_orgs'
          AND NOT (coalesce(pg_get_expr(p.polqual, p.polrelid), '') LIKE '%rls_visible_org_ids%'
                   AND coalesce(pg_get_expr(p.polqual, p.polrelid), '') LIKE '%COALESCE%');
        IF stragglers IS NOT NULL THEN
            RAISE EXCEPTION 'V1_406: org-scoped policy(ies) not on the single-eval InitPlan shape (ensure #1029/V1_405 revert applied first): %', stragglers;
        END IF;
    END
$$;

DROP FUNCTION pg_temp.v1_406_orbased_orgs_policies();
