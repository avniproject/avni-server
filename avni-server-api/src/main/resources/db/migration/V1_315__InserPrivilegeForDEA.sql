-- Introduce 'ViewEditEntitiesOnDataEntryApp' privilege
insert into privilege (uuid, name, description, entity_type, type, created_date_time, last_modified_date_time)
VALUES (uuid_generate_v4(), 'View Or Edit Entities On DataEntry App',
        'Perform CRUD on Transactional data in DataEntry App', 'TransactionDataOnDataEntryApp',
        'ViewEditEntitiesOnDataEntryApp', current_timestamp, current_timestamp);


-- Enable 'ViewEditEntitiesOnDataEntryApp', on all groups that have has_all_privileges set to false
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
where privilege.entity_type in ('TransactionDataOnDataEntryApp');

