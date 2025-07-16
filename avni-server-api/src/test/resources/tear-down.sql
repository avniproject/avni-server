DELETE FROM non_applicable_form_element where 1 = 1;
DELETE FROM form_element where 1 = 1;
DELETE FROM form_element_group where 1 = 1;
DELETE FROM form_mapping where 1 = 1;
DELETE FROM decision_concept where 1 = 1;
DELETE FROM form where 1 = 1;
DELETE FROM user_subject where 1 = 1;
DELETE FROM encounter where 1 = 1;
DELETE FROM program_encounter where 1 = 1;
DELETE FROM program_enrolment where 1 = 1;
DELETE FROM subject_migration where 1 = 1;
delete from group_subject where 1 = 1;
delete from public.user_subject_assignment where 1 = 1;
DELETE FROM entity_approval_status where 1 = 1;
DELETE FROM individual where 1 = 1;
DELETE
FROM operational_program
where 1 = 1;
DELETE
FROM concept_answer
where 1 = 1;
DELETE
FROM answer_concept_migration
where 1 = 1;
DELETE
FROM concept
where 1 = 1;
delete
from group_role
where 1 = 1;
DELETE
FROM individual_relationship
where 1 = 1;
DELETE
FROM individual_relationship_type
where 1 = 1;
DELETE
FROM individual_relation_gender_mapping
where 1 = 1;
DELETE
FROM individual_relation
where 1 = 1;
DELETE
FROM group_privilege
where 1 = 1;
DELETE
FROM operational_program
where 1 = 1;
DELETE
FROM program
where 1 = 1;
DELETE
FROM operational_encounter_type
where 1 = 1;
DELETE
FROM encounter_type
where 1 = 1;
DELETE
FROM gender
where 1 = 1;
DELETE
FROM catchment_address_mapping
where 1 = 1;
DELETE
FROM location_location_mapping
where 1 = 1;
DELETE
FROM address_level
where 1 = 1;
DELETE
FROM address_level_type
where 1 = 1;
DELETE
FROM catchment
where 1 = 1;
DELETE
FROM account_admin
where admin_id <> (select id from users where username = 'admin');
DELETE
FROM user_group
where 1 = 1;
DELETE FROM external_system_config where 1 = 1;
DELETE FROM organisation_config where 1 = 1;
delete from message_request_queue where 1 = 1;
delete from message_receiver where 1 = 1;
delete from message_rule where 1 = 1;
delete from identifier_user_assignment where 1 = 1;
delete from identifier_source where 1 = 1;
DELETE FROM reset_sync where 1 = 1;
DELETE FROM operational_subject_type where 1 = 1;
DELETE FROM subject_type where 1 = 1;
delete from group_role where 1 = 1;
delete from dashboard_section_card_mapping where 1 = 1;
delete from report_card where 1 = 1;
delete from group_dashboard where 1 = 1;
delete from dashboard_section where 1 = 1;
delete from dashboard_filter where 1 = 1;
delete from dashboard where 1 = 1;
DELETE FROM groups where 1 = 1;
delete from storage_management_config where 1 = 1;
DELETE FROM users where username <> 'admin';
DELETE FROM organisation where name <> 'OpenCHS';
DELETE FROM audit where 1 = 1;

ALTER SEQUENCE non_applicable_form_element_id_seq RESTART WITH 1;
ALTER SEQUENCE form_element_id_seq RESTART WITH 1;
ALTER SEQUENCE form_element_group_id_seq RESTART WITH 1;
ALTER SEQUENCE form_mapping_id_seq RESTART WITH 1;
ALTER SEQUENCE form_id_seq RESTART WITH 1;
ALTER SEQUENCE encounter_id_seq RESTART WITH 1;
ALTER SEQUENCE program_encounter_id_seq RESTART WITH 1;
ALTER SEQUENCE program_enrolment_id_seq RESTART WITH 1;
ALTER SEQUENCE individual_id_seq RESTART WITH 1;
ALTER SEQUENCE program_id_seq RESTART WITH 1;
ALTER SEQUENCE encounter_type_id_seq RESTART WITH 1;
ALTER SEQUENCE concept_answer_id_seq RESTART WITH 1;
ALTER SEQUENCE concept_id_seq RESTART WITH 1;
ALTER SEQUENCE gender_id_seq RESTART WITH 1;
ALTER SEQUENCE catchment_address_mapping_id_seq RESTART WITH 1;
ALTER SEQUENCE address_level_id_seq RESTART WITH 1;
ALTER SEQUENCE catchment_id_seq RESTART WITH 1;
ALTER SEQUENCE users_id_seq RESTART WITH 2;
ALTER SEQUENCE organisation_id_seq RESTART WITH 2;
ALTER SEQUENCE individual_relation_id_seq RESTART WITH 1;
ALTER SEQUENCE individual_relation_gender_mapping_id_seq RESTART WITH 1;
ALTER SEQUENCE individual_relationship_type_id_seq RESTART WITH 1;
ALTER SEQUENCE individual_relationship_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_id_seq RESTART WITH 1;
ALTER SEQUENCE external_system_config_id_seq RESTART WITH 1;
ALTER SEQUENCE identifier_source_id_seq RESTART WITH 1;
ALTER SEQUENCE identifier_user_assignment_id_seq RESTART WITH 1;
ALTER SEQUENCE location_location_mapping_id_seq RESTART WITH 1;
