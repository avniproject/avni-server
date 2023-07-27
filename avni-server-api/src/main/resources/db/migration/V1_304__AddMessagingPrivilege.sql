update privilege set entity_type = 'Analytics' where type = 'Analytics';
INSERT INTO privilege(uuid, name, entity_type , type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Messaging', 'Messaging', 'Messaging', 'Ability to send messages', now(), now(), false);
