create function assert_subject_with_same_sync_disabled(bool, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;

create function assert_one_of_subjects_with_sync_disabled(bool, bigint, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;

alter table encounter drop constraint constraint_encounter_sync_disabled_same_as_individual;
alter table encounter
    add constraint constraint_encounter_sync_disabled_same_as_individual
        check (assert_subject_with_same_sync_disabled(sync_disabled, id)) NOT VALID;

alter table individual_relationship
    add constraint const_individual_relationship_sync_disabled_same_as_ind_a
        check (assert_one_of_subjects_with_sync_disabled(sync_disabled, individual_a_id, individual_b_id)) NOT VALID;

alter table subject_program_eligibility
    add constraint const_subject_program_eligibility_sync_disabled_same_as_ind
        check (assert_subject_with_same_sync_disabled(sync_disabled, subject_id)) NOT VALID;

create function checklist_sync_disabled_same_as_individual(bool, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;
alter table checklist
    add constraint constraint_checklist_sync_disabled_same_as_individual
        check (checklist_sync_disabled_same_as_individual(sync_disabled, program_enrolment_id)) NOT VALID;

create function checklist_item_sync_disabled_same_as_individual(bool, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;
alter table checklist_item
    add constraint constraint_checklist_item_sync_disabled_same_as_individual
        check (checklist_item_sync_disabled_same_as_individual(sync_disabled, checklist_id)) NOT VALID;

alter table comment
    add constraint constraint_comment_sync_disabled_same_as_individual
        check (assert_subject_with_same_sync_disabled(sync_disabled, subject_id)) NOT VALID;

-- There is no field on comment_thread to link it to individual directly or indirectly at insert stage. that is unchecked by this.
create function comment_thread_sync_disabled_same_as_individual(bool, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;
alter table comment_thread
    add constraint constraint_comment_thread_sync_disabled_same_as_individual
        check (comment_thread_sync_disabled_same_as_individual(sync_disabled, id)) NOT VALID;

alter table entity_approval_status
    add constraint constraint_entity_approval_status_sync_disabled_same_as_ind
        check (assert_subject_with_same_sync_disabled(sync_disabled, individual_id)) NOT VALID;

alter table group_subject
    add constraint constraint_group_subject_status_sync_disabled_same_as_ind
        check (assert_one_of_subjects_with_sync_disabled(sync_disabled, group_subject_id, member_subject_id)) NOT VALID;

create function program_encounter_sync_disabled_same_as_individual(bool, bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise 'Not Implemented.';
end
$$;
alter table program_encounter
    add constraint constraint_program_encounter_sync_disabled_same_as_individual
        check (program_encounter_sync_disabled_same_as_individual(sync_disabled, program_enrolment_id)) NOT VALID;

alter table program_enrolment
    add constraint constraint_program_enrolment_sync_disabled_same_as_individual
        check (assert_subject_with_same_sync_disabled(sync_disabled, individual_id)) NOT VALID;

alter table subject_migration
    add constraint constraint_subject_migration_sync_disabled_same_as_individual
        check (assert_subject_with_same_sync_disabled(sync_disabled, individual_id)) NOT VALID;

alter table user_subject_assignment
    add constraint constraint_user_subject_assignment_sync_disabled_same_as_ind
        check (assert_subject_with_same_sync_disabled(sync_disabled, subject_id)) NOT VALID;
