INSERT INTO privilege(uuid, name, entity_type, type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Manage Calendars', 'Calendar', 'ManageCalendars',
        'Ability to manage holiday calendars and date markers', now(), now(), false);

insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id,
                             encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id,
                             last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),
       groups.id,
       privilege.id,
       null,
       null,
       null,
       null,
       null,
       1,
       groups.organisation_id,
       1,
       1,
       current_timestamp,
       current_timestamp,
       true
from privilege
         join groups on has_all_privileges = false and organisation_id <> 1
where privilege.type = 'ManageCalendars';
