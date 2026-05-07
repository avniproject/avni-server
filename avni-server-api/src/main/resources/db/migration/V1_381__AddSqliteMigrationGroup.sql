-- Seed the well-known "SQLite Migration" group in every existing organisation.
-- Membership in this group activates the SQLite backend on the avni-client app
-- (instead of Realm). The flag is delivered to the client via the existing
-- MyGroups sync entity.
--
-- Uses a fixed reserved UUID across all orgs (intentionally shared) so that
-- bundle imports and client-side identification are robust to renames and
-- cross-org operations.
INSERT INTO groups (uuid, name, version, organisation_id,
                    created_by_id, last_modified_by_id,
                    created_date_time, last_modified_date_time,
                    has_all_privileges, is_voided)
SELECT 'e6e5e4e3-e2e1-4f00-8000-d0d1d2d3d4d5', 'SQLite Migration', 0, o.id,
       1, 1, current_timestamp, current_timestamp,
       false, false
FROM organisation o
WHERE o.id <> 1
  AND o.is_voided = false
  AND NOT EXISTS (
      SELECT 1 FROM groups g
      WHERE g.organisation_id = o.id AND g.name = 'SQLite Migration'
  );
