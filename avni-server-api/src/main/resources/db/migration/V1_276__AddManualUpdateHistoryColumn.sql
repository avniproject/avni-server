alter table individual
    add column manual_update_history text;
alter table encounter
    add column manual_update_history text;
alter table program_enrolment
    add column manual_update_history text;
alter table program_encounter
    add column manual_update_history text;
alter table checklist
    add column manual_update_history text;
alter table checklist_item
    add column manual_update_history text;
alter table subject_migration
    add column manual_update_history text;
alter table task
    add column manual_update_history text;
alter table user_group
    add column manual_update_history text;
