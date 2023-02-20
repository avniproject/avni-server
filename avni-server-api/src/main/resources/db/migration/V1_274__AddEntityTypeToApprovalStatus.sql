alter table entity_approval_status add column entity_type_uuid text not null default '';

update entity_approval_status easupd set entity_type_uuid = st.uuid, address_id = i.address_id
from entity_approval_status eas
         join individual i on eas.entity_id = i.id
         join subject_type st on i.subject_type_id = st.id
where easupd.entity_type = 'Subject' and easupd.entity_id = i.id;

update entity_approval_status easupd set entity_type_uuid = et.uuid, address_id = e.address_id
    from entity_approval_status eas
         join encounter e on eas.entity_id = e.id
    join encounter_type et on e.encounter_type_id = et.id
where easupd.entity_type = 'Encounter' and easupd.entity_id = e.id;

update entity_approval_status easupd set entity_type_uuid = pet.uuid, address_id = pe.address_id
    from entity_approval_status eas
         join program_encounter pe on eas.entity_id = pe.id
    join encounter_type pet on pe.encounter_type_id = pet.id
where easupd.entity_type = 'ProgramEncounter' and easupd.entity_id = pe.id;

update entity_approval_status easupd set entity_type_uuid = p.uuid, address_id = pe.address_id
    from entity_approval_status eas
    join program_enrolment pe on eas.entity_id = pe.id
    join program p on pe.program_id = p.id
where easupd.entity_type = 'ProgramEnrolment' and easupd.entity_id = pe.id;

update entity_approval_status easupd set entity_type_uuid = p.uuid, address_id = pe.address_id
    from entity_approval_status eas join checklist_item ci on eas.entity_id = ci.id
    join checklist cl on ci.checklist_id = cl.id
    join program_enrolment pe on cl.program_enrolment_id = pe.id
    join program p on pe.program_id = p.id
where easupd.entity_type = 'ChecklistItem' and easupd.entity_id = ci.id;