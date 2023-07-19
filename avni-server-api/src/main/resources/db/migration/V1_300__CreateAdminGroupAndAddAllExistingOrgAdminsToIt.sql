insert into groups (uuid, name, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, has_all_privileges)
    (select uuid_generate_v4(), 'Administrators', 1, id, 1, 1, current_timestamp, current_timestamp, true from organisation where id <> 1 and organisation.is_voided = false);
insert into user_group (uuid, user_id, group_id, is_voided, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
    (select uuid_generate_v4(), users.id, groups.id, false, 1, groups.organisation_id, 1, 1, current_timestamp, current_timestamp from users
        join groups on groups.name = 'Administrators'
     where users.is_voided = false and is_org_admin = true and groups.organisation_id = users.organisation_id);
