INSERT INTO privilege(uuid, name, entity_type , type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Assign Subject', 'Subject', 'AssignSubject', 'Assign Subject to User', now(), now(), false);
