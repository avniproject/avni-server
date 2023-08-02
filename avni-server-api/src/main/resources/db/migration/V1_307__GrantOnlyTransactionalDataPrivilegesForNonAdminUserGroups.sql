begin transaction;

-- 1. Fetch list of all non admin groups with hasAllPrivileges set to true
select * from groups
where has_all_privileges = true and name <> 'Administrators' and organisation_id <> 1;

-- 2.a disable 'Analytics', 'Messaging', 'NonTransaction', 'Task' privileges
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(), groups.id, privilege.id,null,null,null,null,null,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,false
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
where privilege.entity_type in ('Analytics', 'Messaging', 'NonTransaction', 'Task');


-- 2.b enable all transactional privileges : Checklist and ChecklistItem
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),groups.id,privilege.id,null,null,null,null,cd.id,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,true
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
    join checklist_detail cd on cd.is_voided = false and groups.organisation_id = cd.organisation_id
where privilege.entity_type in ('Checklist', 'ChecklistItem');

-- 2.b enable all transactional privileges: Subject
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),groups.id,privilege.id,subject_type.id,null,null,null,null,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,true
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
    join form_mapping on form_mapping.is_voided = false and form_mapping.entity_id is null and form_mapping.observations_type_entity_id is null
    join subject_type on subject_type.is_voided = false and form_mapping.subject_type_id = subject_type.id
where privilege.entity_type = 'Subject' and groups.organisation_id = subject_type.organisation_id;

-- 2.b enable all transactional privileges: general encounter
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),groups.id,privilege.id,subject_type.id,null,null,encounter_type.id,null,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,true
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
    join form_mapping on form_mapping.is_voided = false and form_mapping.entity_id is null
    join subject_type on subject_type.is_voided = false and subject_type.id = form_mapping.subject_type_id
    join encounter_type on encounter_type.is_voided = false and encounter_type.id = form_mapping.observations_type_entity_id
where privilege.entity_type = 'Encounter' and groups.organisation_id = subject_type.organisation_id;

-- 2.b enable all transactional privileges: program encounter
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),groups.id,privilege.id,subject_type.id,program.id,encounter_type.id,null,null,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,true
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
    join form_mapping on form_mapping.is_voided = false
    join subject_type on subject_type.is_voided = false and subject_type.id = form_mapping.subject_type_id
    join encounter_type on encounter_type.is_voided = false and encounter_type.id = form_mapping.observations_type_entity_id
    join program on program.is_voided = false and form_mapping.entity_id = program.id
where privilege.entity_type = 'Encounter' and groups.organisation_id = subject_type.organisation_id;

-- 2.b enable all transactional privileges: program enrolment
insert into group_privilege (uuid, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, version, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time, allow)
select uuid_generate_v4(),groups.id,privilege.id,subject_type.id,program.id,null,null,null,1,groups.organisation_id,1,1,current_timestamp,current_timestamp,true
from privilege join groups on has_all_privileges = true and groups.name <> 'Administrators' and organisation_id <> 1
    join form_mapping on form_mapping.is_voided = false and form_mapping.observations_type_entity_id is null
    join subject_type on subject_type.is_voided = false and subject_type.id = form_mapping.subject_type_id
    join program on program.is_voided = false and form_mapping.entity_id = program.id
where privilege.entity_type = 'Enrolment' and groups.organisation_id = subject_type.organisation_id;

-- 2.c disable hasAllPrivileges
update groups set has_all_privileges = false
where has_all_privileges = true and name <> 'Administrators' and organisation_id <> 1;

rollback;
--- end