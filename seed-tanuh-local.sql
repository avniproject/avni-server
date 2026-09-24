-- Minimal Tanuh-shaped seed for local Path B testing (curl validation
-- of the /api/impl/* endpoints, without a webapp login flow).
--
-- Creates:
--   - one organisation: tanuh_uat
--   - one user matching the staging Cognito identity himeshr@tanuh_uat
--   - 4-level AddressLevelType + AddressLevel hierarchy: Karnataka → Belgavi → Chikodi → Ankali
--   - one Catchment linking the Ankali village
--   - one EncounterType "Physician Review Form"
--
-- Safe to re-run: every insert is idempotent via ON CONFLICT DO NOTHING.

BEGIN;

-- ----- organisation -----
INSERT INTO organisation
  (id, name, db_user, media_directory, uuid, parent_organisation_id, schema_name, category_id, status_id)
VALUES
  (100, 'tanuh_uat', 'tanuh_uat', 'tanuh_uat',
   '11111111-1111-1111-1111-000000000100', 1, 'tanuh_uat', 1, 1)
ON CONFLICT (uuid) DO NOTHING;

-- ----- address_level_type chain (State 1 → District 2 → Block 3 → Village 4) -----
INSERT INTO address_level_type
  (id, name, uuid, organisation_id, version, level, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (100, 'State', '11111111-1111-1111-1111-000000000110', 100, 1, 1, NULL,
        1, 1, now(), now())
ON CONFLICT (name, organisation_id) DO NOTHING;

INSERT INTO address_level_type
  (id, name, uuid, organisation_id, version, level, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (101, 'District', '11111111-1111-1111-1111-000000000111', 100, 1, 2, 100,
        1, 1, now(), now())
ON CONFLICT (name, organisation_id) DO NOTHING;

INSERT INTO address_level_type
  (id, name, uuid, organisation_id, version, level, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (102, 'Block', '11111111-1111-1111-1111-000000000112', 100, 1, 3, 101,
        1, 1, now(), now())
ON CONFLICT (name, organisation_id) DO NOTHING;

INSERT INTO address_level_type
  (id, name, uuid, organisation_id, version, level, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (103, 'Village', '11111111-1111-1111-1111-000000000113', 100, 1, 4, 102,
        1, 1, now(), now())
ON CONFLICT (name, organisation_id) DO NOTHING;

-- ----- address_level rows: lineage is dot-joined ids (matches AddressLevel.calculateLineage) -----
INSERT INTO address_level
  (id, title, uuid, version, organisation_id, type_id, lineage, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (1000, 'Karnataka', '11111111-1111-1111-1111-000000001000', 1, 100, 100,
        '1000', NULL, 1, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO address_level
  (id, title, uuid, version, organisation_id, type_id, lineage, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (1001, 'Belgavi', '11111111-1111-1111-1111-000000001001', 1, 100, 101,
        '1000.1001', 1000, 1, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO address_level
  (id, title, uuid, version, organisation_id, type_id, lineage, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (1002, 'Chikodi', '11111111-1111-1111-1111-000000001002', 1, 100, 102,
        '1000.1001.1002', 1001, 1, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO address_level
  (id, title, uuid, version, organisation_id, type_id, lineage, parent_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (1003, 'Ankali', '11111111-1111-1111-1111-000000001003', 1, 100, 103,
        '1000.1001.1002.1003', 1002, 1, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----- catchment -----
INSERT INTO catchment
  (id, name, uuid, version, organisation_id, type,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES (100, 'Tanuh UAT Catchment', '11111111-1111-1111-1111-000000000200',
        1, 100, 'TANUH', 1, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO catchment_address_mapping (catchment_id, addresslevel_id)
VALUES (100, 1003) ON CONFLICT DO NOTHING;

-- ----- user matching the staging Cognito identity -----
INSERT INTO users
  (id, username, uuid, organisation_id, operating_individual_scope,
   is_org_admin, catchment_id, name)
VALUES (1000, 'himeshr@tanuh_uat',
        '11111111-1111-1111-1111-000000000300', 100, 'ByCatchment',
        false, 100, 'Himesh R')
ON CONFLICT (id) DO NOTHING;

-- ----- encounter type -----
INSERT INTO encounter_type
  (name, uuid, version, organisation_id,
   created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
VALUES ('Physician Review Form', '11111111-1111-1111-1111-000000000400',
        1, 100, 1, 1, now(), now())
ON CONFLICT (uuid, organisation_id) DO NOTHING;

COMMIT;

-- Sanity output
SELECT 'organisation'        AS what, COUNT(*) AS n FROM organisation        WHERE id = 100
UNION ALL SELECT 'user',             COUNT(*) FROM users                     WHERE username = 'himeshr@tanuh_uat'
UNION ALL SELECT 'address_level_types', COUNT(*) FROM address_level_type     WHERE organisation_id = 100
UNION ALL SELECT 'address_levels',    COUNT(*) FROM address_level            WHERE organisation_id = 100
UNION ALL SELECT 'catchment',         COUNT(*) FROM catchment                WHERE id = 100
UNION ALL SELECT 'catchment_mapping', COUNT(*) FROM catchment_address_mapping WHERE catchment_id = 100
UNION ALL SELECT 'encounter_type',    COUNT(*) FROM encounter_type           WHERE name = 'Physician Review Form';
