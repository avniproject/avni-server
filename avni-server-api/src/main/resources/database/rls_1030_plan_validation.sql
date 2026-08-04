-- avniproject/avni-server#1030 | Prod-scale plan + timing validation for the single-eval RLS re-do (V1_406).
--
-- WHY THIS EXISTS
--   #1008 passed prerelease (idle DB, plan-shape + correctness only) yet collapsed prod, because the per-tuple
--   re-evaluation cost is a TIMING signal that only appears at prod scale. And #1030's original spec was
--   plan-validated with the predicate INLINE in a WHERE clause -- which does not transfer: policy quals are
--   securityQuals and plan differently. So the acceptance gate is EXPLAIN (ANALYZE, BUFFERS) TIMING at prod
--   scale, measured THROUGH THE ACTUAL POLICIES under SET ROLE.
--
-- WHERE TO RUN
--   BEFORE (current reverted V1_405 policies): the prod read-replica -- identical data + policies via WAL, no
--   load on the primary.
--   AFTER (V1_406 policies): NOT the streaming replica -- a hot standby is in recovery and rejects ALL catalog
--   DDL (CREATE/DROP POLICY included, superuser or not), so the new policies cannot be applied there even inside
--   a rolled-back transaction. Use a WRITABLE prod-scale copy: a clone restored from the latest
--   snapshot/backup (preferred), apply V1_405+V1_406 to it, and run this file there. Plan SHAPE (not timing) is
--   additionally pinned by RlsPolicyPlanShapeIntegrationTest, which EXPLAINs through a real policy under
--   SET ROLE on a 100k-row probe.
--
-- HOW TO RUN
--   psql -h <host> -d <db> -f rls_1030_plan_validation.sql
--   Prompts for: org db_user (acceptance targets: Goonj id 391, APF), a subject_type_id, and a comma-separated
--   catchment address_id list (take both from a real device's sync scope for that org).
--
-- WHAT TO LOOK FOR (AFTER, per query)
--   * InitPlan 1 (returns $0)  -- the visible-org-ids function evaluated ONCE per query.
--   * Index (Only) Scan with Index Cond: (organisation_id = ANY (COALESCE($0, '{}'::integer[])))  -- index-served,
--     no Seq Scan on the big relation, and NO `Filter: (hashed SubPlan N)` (that is the no-op signature of the
--     reverted/unnest shapes).
--   * Total time sub-second / low-second vs the OR-based seq-scan BEFORE.

\timing on

\prompt 'Org db_user to SET ROLE as (acceptance targets: Goonj, APF): ' org_db_user
\prompt 'A subject_type_id for the syncDetails-style count: ' subject_type_id
\prompt 'Comma-separated catchment address_level ids (e.g. 101,102,103): ' address_ids

-- Sanity: the role must resolve to exactly one organisation.
SELECT id, name, db_user FROM public.organisation WHERE db_user = :'org_db_user';

SET ROLE :"org_db_user";
SELECT current_user AS acting_as;                 -- must be the org db_user, so RLS scopes to this org

-- Sanity: the visible-org-id sets are small (own org + group; +ancestors for ref).
SELECT public.rls_visible_org_ids()                AS tx_visible_org_ids,
       public.rls_visible_org_ids_with_ancestors() AS ref_visible_org_ids;

-- ---- Acceptance queries (AC-named tables). All are org-predicate-dominant reference/scope reads. ----

-- group_privilege (tx; the trivial query that was 35ms off / 1,573ms on under #1008)
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM public.group_privilege;

-- address_level (ref; large in big geographies -- exercises the with_ancestors variant)
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM public.address_level;

-- individual (tx; the largest per-org table)
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM public.individual;

-- syncDetails-style scope count: subject_program_eligibility joined to individual over a catchment.
-- The last_modified window mirrors a routine incremental sync.
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(spe.id)
FROM public.subject_program_eligibility spe
JOIN public.individual s ON s.id = spe.subject_id
WHERE spe.last_modified_date_time > now() - interval '7 days'
  AND s.subject_type_id = :subject_type_id
  AND s.address_id IN (:address_ids)
  AND spe.sync_disabled = false;

-- task change-detection count (non-scope; org-predicate + last_modified via V1_404's composite)
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM public.task WHERE last_modified_date_time > now() - interval '7 days';

RESET ROLE;

-- Note: on the read-replica (BEFORE runs), long seq scans can be cancelled by recovery conflicts
-- (max_standby_streaming_delay); re-run the query if that happens.
