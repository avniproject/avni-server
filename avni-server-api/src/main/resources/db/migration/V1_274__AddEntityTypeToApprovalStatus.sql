alter table entity_approval_status
    add column entity_type_uuid text not null default '',
    add column address_id bigint,
    add column individual_id bigint,
    add column sync_concept_1_value text,
    add column sync_concept_2_value text;

update entity_approval_status easupd set entity_type_uuid = st.uuid,
                                         address_id = i.address_id,
                                         individual_id = i.id,
                                         sync_concept_1_value = i.sync_concept_1_value,
                                         sync_concept_2_value = i.sync_concept_2_value
from entity_approval_status eas
         join individual i on eas.entity_id = i.id
         join subject_type st on i.subject_type_id = st.id
where easupd.entity_type = 'Subject' and easupd.entity_id = i.id;

update entity_approval_status easupd set entity_type_uuid = et.uuid,
                                         address_id = e.address_id,
                                         individual_id = e.individual_id,
                                         sync_concept_1_value = e.sync_concept_1_value,
                                         sync_concept_2_value = e.sync_concept_2_value
    from entity_approval_status eas
         join encounter e on eas.entity_id = e.id
    join encounter_type et on e.encounter_type_id = et.id
where easupd.entity_type = 'Encounter' and easupd.entity_id = e.id;

update entity_approval_status easupd set entity_type_uuid = pet.uuid,
                                         address_id = pe.address_id,
                                         individual_id = pe.individual_id,
                                         sync_concept_1_value = pe.sync_concept_1_value,
                                         sync_concept_2_value = pe.sync_concept_2_value
    from entity_approval_status eas
         join program_encounter pe on eas.entity_id = pe.id
    join encounter_type pet on pe.encounter_type_id = pet.id
where easupd.entity_type = 'ProgramEncounter' and easupd.entity_id = pe.id;

update entity_approval_status easupd set entity_type_uuid = p.uuid,
                                         address_id = pe.address_id,
                                         individual_id = pe.individual_id,
                                         sync_concept_1_value = pe.sync_concept_1_value,
                                         sync_concept_2_value = pe.sync_concept_2_value
    from entity_approval_status eas
    join program_enrolment pe on eas.entity_id = pe.id
    join program p on pe.program_id = p.id
where easupd.entity_type = 'ProgramEnrolment' and easupd.entity_id = pe.id;

update entity_approval_status easupd set entity_type_uuid = p.uuid,
                                         address_id = pe.address_id,
                                         individual_id = pe.individual_id,
                                         sync_concept_1_value = pe.sync_concept_1_value,
                                         sync_concept_2_value = pe.sync_concept_2_value
    from entity_approval_status eas join checklist_item ci on eas.entity_id = ci.id
    join checklist cl on ci.checklist_id = cl.id
    join program_enrolment pe on cl.program_enrolment_id = pe.id
    join program p on pe.program_id = p.id
where easupd.entity_type = 'ChecklistItem' and easupd.entity_id = ci.id;

alter table entity_approval_status
    ADD CONSTRAINT entity_approval_status_address_id
        FOREIGN KEY (address_id) REFERENCES address_level (id);

alter table entity_approval_status
    ADD CONSTRAINT entity_approval_status_individual_id
        FOREIGN KEY (individual_id) REFERENCES individual (id);

create index entity_approval_status_sync_1_index
    on entity_approval_status (address_id, last_modified_date_time, organisation_id, entity_type, entity_type_uuid);

create index entity_approval_status_sync_2_index
    on entity_approval_status (individual_id, last_modified_date_time, organisation_id, entity_type, entity_type_uuid);

create index entity_approval_status_sync_3_index
    on entity_approval_status (sync_concept_1_value, last_modified_date_time, organisation_id, entity_type, entity_type_uuid);

create index entity_approval_status_sync_4_index
    on entity_approval_status (sync_concept_1_value, sync_concept_2_value, last_modified_date_time, organisation_id,
                               entity_type, entity_type_uuid);

create index entity_approval_status_sync_5_index
    on entity_approval_status (address_id, individual_id, sync_concept_1_value, sync_concept_2_value,
                               last_modified_date_time, organisation_id, entity_type, entity_type_uuid);

alter table entity_approval_status
    ALTER column individual_id SET not null;
alter table entity_approval_status
    ALTER column individual_id SET default 1;
