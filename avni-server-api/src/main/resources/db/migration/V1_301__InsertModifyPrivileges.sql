-- Rename reports to Analytics
UPDATE privilege SET name = 'Analytics', description = 'Reports and Media', entity_type = 'Report', type='Analytics' WHERE name = 'Report';

-- Insert EditNews privilege
INSERT INTO privilege(uuid, name, entity_type , type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Edit News', 'NonTransaction', 'EditNews', 'Edit and publish news broadcasts', now(), now(), false);