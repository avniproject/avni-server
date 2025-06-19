create function encounter_sync_disabled_same_as_individual(syncDisabled bool, encounterId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;

alter table encounter
    add constraint constraint_encounter_sync_disabled_same_as_individual
        check (encounter_sync_disabled_same_as_individual(sync_disabled, id));
