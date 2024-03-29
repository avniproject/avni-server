
DROP VIEW IF EXISTS mother_program_encounters;
DROP VIEW IF EXISTS mother_program_enrolments;
DROP VIEW IF EXISTS checklist_items;
DROP VIEW IF EXISTS adolescents;
DROP VIEW IF EXISTS adolescent_visit;
DROP VIEW IF EXISTS adolescent_visit_summary;

DROP FUNCTION IF EXISTS coded_obs( ANYELEMENT, TEXT );
DROP FUNCTION IF EXISTS coded_obs_exists( ANYELEMENT, TEXT );
DROP FUNCTION IF EXISTS concept_uuid( TEXT );
DROP FUNCTION IF EXISTS date_obs( ANYELEMENT, TEXT );
DROP FUNCTION IF EXISTS is_overdue_visit( PROGRAM_ENCOUNTER );
DROP FUNCTION IF EXISTS numeric_obs( ANYELEMENT, TEXT );
DROP FUNCTION IF EXISTS text_obs( ANYELEMENT, TEXT );
DROP FUNCTION IF EXISTS coded_obs_contains(ANYELEMENT, TEXT, TEXT);
DROP FUNCTION IF EXISTS one_of_coded_obs_contains(ANYELEMENT, TEXT[], TEXT);
DROP FUNCTION IF EXISTS in_one_entity_coded_obs_contains(program_enrolment, program_encounter, TEXT, TEXT);
DROP FUNCTION IF EXISTS one_of_coded_obs_exists(ANYELEMENT, TEXT[]);
DROP FUNCTION IF EXISTS has_dropped_out(program_enrolment, program_encounter);
DROP FUNCTION IF EXISTS has_problem(JSONB);
DROP FUNCTION IF EXISTS is_counselled(JSONB);
DROP FUNCTION IF EXISTS has_dropped_out(JSONB, JSONB);
DROP FUNCTION IF EXISTS numeric_obs(JSONB, TEXT);
DROP FUNCTION IF EXISTS coded_obs(JSONB, TEXT);
DROP FUNCTION IF EXISTS coded_obs_exists(JSONB, TEXT);
DROP FUNCTION IF EXISTS coded_obs_contains(ANYELEMENT, TEXT, TEXT);
DROP FUNCTION IF EXISTS one_of_coded_obs_contains(JSONB, TEXT[], TEXT);
DROP FUNCTION IF EXISTS in_one_entity_coded_obs_contains(JSONB, JSONB, TEXT, TEXT);
DROP FUNCTION IF EXISTS one_of_coded_obs_exists(ANYELEMENT, TEXT[]);
DROP FUNCTION IF EXISTS has_problem(program_encounter);
DROP FUNCTION IF EXISTS is_counselled( program_encounter );
DROP FUNCTION IF EXISTS in_one_entity_coded_obs_contains(program_enrolment,program_encounter,text,text[]);


DROP TABLE IF EXISTS rule CASCADE;
DROP TABLE IF EXISTS rule_dependency CASCADE;
DROP TABLE IF EXISTS program_encounter CASCADE;
DROP TABLE IF EXISTS encounter CASCADE;
DROP TABLE IF EXISTS checklist_item CASCADE ;
DROP TABLE IF EXISTS checklist CASCADE ;
DROP TABLE IF EXISTS program_enrolment CASCADE ;
DROP TABLE IF EXISTS individual CASCADE ;
DROP TABLE IF EXISTS operational_program CASCADE ;
DROP TABLE IF EXISTS program CASCADE ;
DROP TABLE IF EXISTS form_mapping CASCADE ;
DROP TABLE IF EXISTS operational_encounter_type CASCADE ;
DROP TABLE IF EXISTS encounter_type CASCADE ;
DROP TABLE IF EXISTS form_element CASCADE ;
DROP TABLE IF EXISTS form_element_group CASCADE ;
DROP TABLE IF EXISTS form CASCADE ;
DROP TABLE IF EXISTS catchment_address_mapping CASCADE;
DROP TABLE IF EXISTS address_level CASCADE ;
DROP TABLE IF EXISTS catchment CASCADE ;
DROP TABLE IF EXISTS gender CASCADE ;
DROP TABLE IF EXISTS concept_answer CASCADE ;
DROP TABLE IF EXISTS concept CASCADE ;
DROP TABLE IF EXISTS health_metadata_version CASCADE ;
DROP TABLE IF EXISTS non_applicable_form_element CASCADE ;
DROP TABLE IF EXISTS users CASCADE ;
DROP TABLE IF EXISTS individual_relation_master CASCADE ;
DROP TABLE IF EXISTS individual_relative CASCADE ;
DROP TABLE IF EXISTS audit CASCADE ;
DROP TABLE IF EXISTS organisation CASCADE ;
DROP TABLE IF EXISTS schema_version CASCADE ;
DROP TABLE IF EXISTS flyway_schema_history CASCADE ;
DROP TABLE IF EXISTS organisation CASCADE ;
DROP TABLE IF EXISTS schema_version CASCADE ;
