insert into privilege(uuid, name, description, type, entity_type, created_date_time, last_modified_date_time)
    values (uuid_generate_v4(), 'Void visit', 'Void visit', 'VoidVisit', 'Encounter', current_timestamp, current_timestamp);
update privilege set name = 'Edit relation', type = 'EditRelation' where type = 'EditRelationship';
