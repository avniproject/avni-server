--- Create org conf  if not exists
INSERT INTO organisation_config (uuid, organisation_id, settings, audit_id, version, is_voided,
                             worklist_updation_rule, created_by_id, last_modified_by_id, created_date_time,
                             last_modified_date_time, export_settings)
SELECT uuid_generate_v4(), 1, '{"languages": ["en"]}', create_audit(), 0, false,
       null, 1, 1, now(),
       now(), null
WHERE NOT EXISTS (SELECT * FROM organisation_config);
