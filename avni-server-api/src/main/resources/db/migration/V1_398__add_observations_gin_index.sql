-- #1005: GIN indexes so concept-value lookups (observations @> '{"<uuid>":"<value>"}') use an index
-- instead of a full-table scan. Serves the external API concept filters
-- (/api/subjects, /api/encounters, /api/programEncounters) via CHSRepository.withConceptValues.
-- jsonb_path_ops is smaller/faster than the default jsonb_ops and supports the @> operator we use.
--
-- DEPLOYMENT NOTE: Flyway runs migrations inside a transaction, so CREATE INDEX CONCURRENTLY cannot be
-- used here. On large production tables (e.g. individual ~ 2.5M rows / ~5 GB) a plain CREATE INDEX
-- takes a write lock for the duration of the build. Pre-create these CONCURRENTLY out-of-band BEFORE
-- deploying this migration so the IF NOT EXISTS clauses below become no-ops, e.g.:
--   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_individual_observations_gin
--       ON individual USING gin (observations jsonb_path_ops);
--   (repeat for encounter, program_encounter)

CREATE INDEX IF NOT EXISTS idx_individual_observations_gin
    ON individual USING gin (observations jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_encounter_observations_gin
    ON encounter USING gin (observations jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_program_encounter_observations_gin
    ON program_encounter USING gin (observations jsonb_path_ops);
