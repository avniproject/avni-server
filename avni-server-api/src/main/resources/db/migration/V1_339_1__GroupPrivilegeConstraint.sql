alter table group_privilege add column if not exists impl_version int not null default 1;

update group_privilege
set impl_version = 0
where is_voided = true;

create or replace function check_group_privilege_uniqueness(groupPrivilegeId int, groupId int, privilegeId int, subjectTypeId int, programId int, programEncounterTypeId int, encounterTypeId int, checklistDetailId int, implVersion int) returns boolean
  language plpgsql
as
$$
declare
begin
  if exists (select gp.*
             from public.group_privilege gp
             where gp.group_id = groupId
               and gp.privilege_id = privilegeId
               and (gp.subject_type_id = subjectTypeId or (gp.subject_type_id is null and subjectTypeId is null))
               and (gp.program_id = programId or (gp.program_id is null and programId is null))
               and (gp.program_encounter_type_id = programEncounterTypeId or (gp.program_encounter_type_id is null and programEncounterTypeId is null))
               and (gp.encounter_type_id = encounterTypeId or (gp.encounter_type_id is null and encounterTypeId is null))
               and (gp.checklist_detail_id = checklistDetailId or (gp.checklist_detail_id is null and checklistDetailId is null))
               and gp.id <> groupPrivilegeId
               and gp.impl_version = 1
               and implVersion = 1
               ) then
    raise 'Duplicate group privilege exists for: id: %, group_id: %, privilege_id: % subject_type_id: %, program_id: %, program_encounter_type_id: %, encounter_type_id: %, checklist_detail_id: %', groupPrivilegeId, groupId, privilegeId, subjectTypeId, programId, programEncounterTypeId, encounterTypeId, checklistDetailId;
  end if;

  return true;
end
$$;

alter table group_privilege
  add constraint check_group_privilege_unique
    check (check_group_privilege_uniqueness(id, group_id, privilege_id, subject_type_id, program_id, program_encounter_type_id, encounter_type_id, checklist_detail_id, impl_version));