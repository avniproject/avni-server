delete from group_privilege where id in
  (select group_privilege.id from group_privilege
      join privilege on group_privilege.privilege_id = privilege.id
      where privilege.type in ('EditRelation', 'EditForm'));
delete from privilege where type in ('EditRelation', 'EditForm');

INSERT INTO privilege(uuid, name, entity_type , type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Edit Task Type', 'NonTransaction', 'EditTaskType', 'Edit task type', now(), now(), false);
