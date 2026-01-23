alter table form_mapping 
    drop constraint check_form_mapping_unique;

create or replace function check_form_mapping_uniqueness(organisationId int, subjectTypeId int, entityId bigint,
                                                         observationsTypeEntityId int, taskTypeId int,
                                                         formId bigint,
                                                         formMappingId int, implVersion int, formMappingIsVoided boolean) returns boolean
    language plpgsql
as
$$
declare
    mapping_exists boolean;
begin
    -- Skip validation if mapping is voided
    if formMappingIsVoided = true then
        return true;
    end if;
    
    -- Check form type consistency
    select exists(
        select 1
        from public.form f
        where f.id = formId
          and (
              (f.form_type = 'IndividualProfile' and
               (entityId is not null or observationsTypeEntityId is not null))
                  or
              (f.form_type = 'ProgramEnrolment' and
               (entityId is null or observationsTypeEntityId is not null))
                  or
              (f.form_type = 'ProgramExit' and
               (entityId is null or observationsTypeEntityId is not null))
                  or
              (f.form_type = 'ProgramEncounter' and
               (entityId is null or observationsTypeEntityId is null))
                  or
              (f.form_type = 'ProgramEncounterCancellation' and
               (entityId is null or observationsTypeEntityId is null))
                  or
              (f.form_type = 'Encounter' and
               (entityId is not null or observationsTypeEntityId is null))
                  or
              (f.form_type = 'IndividualEncounterCancellation' and
               (entityId is not null or observationsTypeEntityId is null))
              )
    ) into mapping_exists;
    
    if mapping_exists then
        raise EXCEPTION 'Invalid form mapping(uuid: %): Form(uuid: %) is of type % and hence cannot be mapped % and %.',
               (select fm.uuid from form_mapping fm where fm.id = formMappingId),
               (select f.uuid from form f where f.id = formId),
               (select f.form_type from form f where f.id = formId),
               case when entityId is not null then 'with program' else 'without program' end,
               case when observationsTypeEntityId is not null then 'with encounter type' else 'without encounter type' end
        USING HINT = 'Form type rules: IndividualProfile - no program, no encounter type | ' ||
                   'ProgramEnrolment/Exit - with program, no encounter type | ' ||
                   'ProgramEncounter/Cancellation - with program and encounter type | ' ||
                   'Encounter/IndividualCancellation - no program, with encounter type';
    end if;

    -- Check for duplicate mappings
    select exists(
        select form_mapping.*
        from public.form
                 inner join form_mapping on form_mapping.form_id = form.id
        where form_mapping.organisation_id = organisationId
          and form_mapping.subject_type_id = subjectTypeId
          and (form_mapping.entity_id = entityId or (form_mapping.entity_id is null and entityId is null))
          and (form_mapping.observations_type_entity_id = observationsTypeEntityId or
               (form_mapping.observations_type_entity_id is null and observationsTypeEntityId is null))
          and (form_mapping.task_type_id = taskTypeId or
               (form_mapping.task_type_id is null and taskTypeId is null))
          and form_mapping.impl_version = 1
          and implVersion = 1
          and form.form_type = (select public.form.form_type from form where id = formId)
          and form_mapping.id <> formMappingId
    ) into mapping_exists;
    
    if mapping_exists then
        raise 'Duplicate form mapping exists for: organisation_id: %, subject_type_id: %, entity_id: %, observations_type_entity_id: %, task_type_id: %. Using formId: %, formMappingId: %.',
              organisationId, subjectTypeId, entityId, observationsTypeEntityId, taskTypeId, formId, formMappingId;
    end if;
    
    return true;
end
$$;

alter table form_mapping
    add constraint check_form_mapping_unique
        check (check_form_mapping_uniqueness(organisation_id, subject_type_id, entity_id, observations_type_entity_id, task_type_id, form_id, id, impl_version, is_voided));
