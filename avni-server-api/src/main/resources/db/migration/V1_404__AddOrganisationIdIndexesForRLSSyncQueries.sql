-- #0 | Restore the organisation_id btrees that V1_398's indexable RLS policy assumes exist.
--
-- ROOT CAUSE
--   V1_398 rewrote every org RLS policy to `organisation_id = ANY(rls_visible_org_ids())`, which is
--   index-pushable *against the existing organisation_id btrees*. Those btrees were created in bulk by the
--   old V1_93_5 migration, which only covered the tables that existed then. Transactional tables added later
--   (subject_program_eligibility, the task_* family, comment(_thread), group_subject, subject_migration,
--   entity_approval_status, session, attendance_record, user_subject_assignment, ...) never got one, so on
--   those tables the new predicate falls back to a SEQ SCAN. The scope-based sync change-detection fan-out in
--   /syncDetails runs a count per entity while holding one connection; multi-second seq scans there hold
--   connections long enough to exhaust the pool even at low traffic (PoolExhaustedException, busy:100 idle:0).
--
-- FIX
--   * (organisation_id, last_modified_date_time) composite on each affected RLS table: the leading column makes
--     the RLS predicate sargable (V1_398's assumption); the trailing column serves the sync audit filter
--     (last_modified_date_time > :loadedSince) in the same index.
--   * subject_id btree on the scope-aware tables whose sync count joins `individual` (subject_program_eligibility,
--     comment) so the planner can nested-loop from the (selective) catchment individuals instead of scanning.
--
-- IDEMPOTENT: every statement is CREATE INDEX IF NOT EXISTS, so it is a no-op where the index already exists.
--
-- PRODUCTION / LARGE POPULATED ENVIRONMENTS: a plain CREATE INDEX takes ACCESS EXCLUSIVE and blocks writes.
--   Pre-create these with `CREATE INDEX CONCURRENTLY IF NOT EXISTS <same name> ...` (see the deploy runbook)
--   BEFORE this migration runs; it then finds them present and skips. The names below match the runbook exactly.

-- ---- Incident-critical: subject_program_eligibility (scope-aware; count joins individual on subject_id) ----
CREATE INDEX IF NOT EXISTS subject_program_eligibility_subject_id_idx
    ON subject_program_eligibility (subject_id);
CREATE INDEX IF NOT EXISTS subject_program_eligibility_org_lmdt_idx
    ON subject_program_eligibility (organisation_id, last_modified_date_time);

-- ---- Incident-critical: task family (non-scope; count filters organisation_id + last_modified_date_time) ----
CREATE INDEX IF NOT EXISTS task_org_lmdt_idx
    ON task (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS task_status_org_lmdt_idx
    ON task_status (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS task_type_org_lmdt_idx
    ON task_type (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS task_unassignment_org_lmdt_idx
    ON task_unassignment (organisation_id, last_modified_date_time);

-- ---- Same root cause: other RLS transactional sync tables missing an organisation_id btree ----
CREATE INDEX IF NOT EXISTS comment_org_lmdt_idx
    ON comment (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS comment_subject_id_idx
    ON comment (subject_id);
CREATE INDEX IF NOT EXISTS comment_thread_org_lmdt_idx
    ON comment_thread (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS group_subject_org_lmdt_idx
    ON group_subject (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS subject_migration_org_lmdt_idx
    ON subject_migration (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS entity_approval_status_org_lmdt_idx
    ON entity_approval_status (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS session_org_lmdt_idx
    ON session (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS attendance_record_org_lmdt_idx
    ON attendance_record (organisation_id, last_modified_date_time);
CREATE INDEX IF NOT EXISTS user_subject_assignment_org_lmdt_idx
    ON user_subject_assignment (organisation_id, last_modified_date_time);
