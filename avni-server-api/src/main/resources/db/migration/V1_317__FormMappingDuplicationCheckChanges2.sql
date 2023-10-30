create or replace function check_form_mapping_uniqueness(organisationId int, subjectTypeId int, entityId bigint, observationsTypeEntityId int, taskTypeId int, formId bigint,
                                              formMappingId int, implVersion int) returns boolean
    language plpgsql
as
$$
declare
begin
    if exists (select form_mapping.*
    from form
             inner join form_mapping on form_mapping.form_id = form.id
    where form_mapping.organisation_id = organisationId
      and form_mapping.subject_type_id = subjectTypeId
      and (form_mapping.entity_id = entityId or (form_mapping.entity_id is null and entityId is null))
      and (form_mapping.observations_type_entity_id = observationsTypeEntityId or (form_mapping.observations_type_entity_id is null and observationsTypeEntityId is null))
      and (form_mapping.task_type_id = taskTypeId or (form_mapping.task_type_id is null and taskTypeId is null))
      and form_mapping.impl_version = 1
      and implVersion = 1
      and form.form_type = (select form.form_type from form where id = formId)
      and form_mapping.id <> formMappingId) then
        raise 'Duplicate form mapping exists for: organisation_id: %, subject_type_id: %, entity_id: %, observations_type_entity_id: %, task_type_id: %. Using formId: %, formMappingId: %.', organisationId, subjectTypeId, entityId, observationsTypeEntityId, taskTypeId, formId, formMappingId;
    end if;

    return true;
end
$$;

alter table form_mapping
    add constraint check_form_mapping_unique
        check (check_form_mapping_uniqueness(organisation_id, subject_type_id, entity_id, observations_type_entity_id, task_type_id, form_id, id, impl_version));
