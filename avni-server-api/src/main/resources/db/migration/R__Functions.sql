DROP FUNCTION IF EXISTS create_db_user(inrolname text, inpassword text);
CREATE OR REPLACE FUNCTION create_db_user(inrolname text, inpassword text)
    RETURNS BIGINT AS
$BODY$
BEGIN
    IF NOT EXISTS(SELECT rolname FROM pg_roles WHERE rolname = inrolname)
    THEN
        EXECUTE 'CREATE ROLE ' || quote_ident(inrolname) || ' NOINHERIT LOGIN PASSWORD ' || quote_literal(inpassword);
    END IF;
    EXECUTE 'GRANT ' || quote_ident(inrolname) || ' TO openchs';
    PERFORM grant_all_on_all(inrolname);
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;


DROP FUNCTION IF EXISTS create_implementation_schema(text);
CREATE OR REPLACE FUNCTION create_implementation_schema(schema_name text, db_user text)
    RETURNS BIGINT AS
$BODY$
BEGIN
    EXECUTE 'CREATE SCHEMA IF NOT EXISTS "' || schema_name || '" AUTHORIZATION "' || db_user || '"';
    EXECUTE 'GRANT ALL PRIVILEGES ON SCHEMA "' || schema_name || '" TO "' || db_user || '"';
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;


CREATE OR REPLACE FUNCTION jsonb_object_values_contain(obs JSONB, pattern TEXT)
    RETURNS BOOLEAN AS
$$
BEGIN
    return EXISTS (select true from jsonb_each_text(obs) where value ilike pattern);
END;
$$
    LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION create_audit(user_id NUMERIC)
    RETURNS INTEGER AS
$$
DECLARE
    result INTEGER;
BEGIN
    INSERT INTO audit(created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
    VALUES (user_id, user_id, now(), now())
    RETURNING id into result;
    RETURN result;
END
$$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_audit()
    RETURNS INTEGER AS
'select create_audit(1)' language sql;

-- These were failing tests, removing them for now.
-- DROP function if exists get_observation_pattern;
-- DROP function if exists get_outer_query(text, text);
-- DROP function if exists get_outer_query(text);
-- DROP function if exists web_search_function;

DROP FUNCTION IF EXISTS create_view(text, text, text);
CREATE OR REPLACE FUNCTION create_view(schema_name text, view_name text, sql_query text)
    RETURNS BIGINT AS
$BODY$
BEGIN
    --     EXECUTE 'set search_path = ' || ;
    EXECUTE 'DROP VIEW IF EXISTS ' || schema_name || '.' || view_name;
    EXECUTE 'CREATE OR REPLACE VIEW ' || schema_name || '.' || view_name || ' AS ' || sql_query;
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;

DROP FUNCTION IF EXISTS drop_view(text, text);
CREATE OR REPLACE FUNCTION drop_view(schema_name text, view_name text)
    RETURNS BIGINT AS
$BODY$
BEGIN
    EXECUTE 'set search_path = ' || schema_name;
    EXECUTE 'DROP VIEW IF EXISTS ' || view_name;
    EXECUTE 'reset search_path';
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;

CREATE OR REPLACE FUNCTION concept_name(TEXT) RETURNS TEXT
    stable
    strict
    language sql
as
$$
SELECT name
FROM concept
WHERE uuid = $1;
$$;

create or replace function get_coded_string_value(obs jsonb, obs_store hstore)
    returns character varying
    language plpgsql
    SECURITY INVOKER
    stable
as
$$
DECLARE
    result VARCHAR;
BEGIN
    BEGIN
        IF JSONB_TYPEOF(obs) = 'array'
        THEN
            select STRING_AGG(obs_store -> OB.UUID, ', ')
            from JSONB_ARRAY_ELEMENTS_TEXT(obs) AS OB (UUID)
            INTO RESULT;
        ELSE
            SELECT obs_store -> (obs ->> 0) INTO RESULT;
        END IF;
        RETURN RESULT;
    EXCEPTION
        WHEN OTHERS
            THEN
                RAISE NOTICE 'Failed while processing get_coded_string_value(''%'')', obs :: TEXT;
                RAISE NOTICE '% %', SQLERRM, SQLSTATE;
    END;
END
$$;


create or replace function append_manual_update_history(current_value text, text_to_be_added text)
    returns text
    language plpgsql
    SECURITY INVOKER
    immutable
as
$$
BEGIN
    if current_value is null or trim(current_value) = '' then
        RETURN concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', text_to_be_added);
    else
        RETURN concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', text_to_be_added, ' || ',
                      current_value);
    end if;
END
$$;

-- Create or replace function to delete etl metadata for an org
drop function if exists delete_etl_table_metadata;
create function delete_etl_table_metadata(in_impl_schema text, in_db_owner text, in_table_name text) returns bool
    language plpgsql
as
$$
BEGIN
    execute 'set role "' || in_db_owner || '";';
    execute 'delete from entity_sync_status where table_metadata_id = (select id from table_metadata where name = ''' ||
            in_table_name || ''' and schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from index_metadata where table_metadata_id = (select id from table_metadata where name = ''' ||
            in_table_name || ''' and schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from column_metadata where table_id = (select id from table_metadata where name = ''' ||
            in_table_name || ''' and schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from table_metadata where name = ''' || in_table_name || ''' and schema_name = ''' ||
            in_impl_schema || ''';';
    execute 'drop table if exists "' || in_impl_schema || '"."' || in_table_name || '"';
    return true;
END
$$;

-- Create or replace function to delete etl metadata for an org
DROP FUNCTION IF EXISTS delete_etl_metadata_for_schema;
create function delete_etl_metadata_for_schema(in_impl_schema text, in_db_user text, in_db_owner text) returns bool
    language plpgsql
as
$$
BEGIN
    execute 'set role "' || in_db_owner || '";';
    execute 'drop schema if exists "' || in_impl_schema || '" cascade;';
    execute 'delete from entity_sync_status where db_user = ''' || in_db_user || ''';';
    execute 'delete from entity_sync_status where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from index_metadata where table_metadata_id in (select id from table_metadata where schema_name = ''' ||
            in_impl_schema || ''');';
    execute 'delete from column_metadata where table_id in (select id from table_metadata where schema_name = ''' ||
            in_impl_schema || ''');';
    execute 'delete from table_metadata where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from post_etl_sync_status where db_user = ''' || in_db_user || ''';';
    return true;
END
$$;

-- Create function to delete etl metadata for org-group
DROP FUNCTION IF EXISTS delete_etl_metadata_for_org;
create function delete_etl_metadata_for_org(in_impl_schema text, in_db_user text) returns bool
    language plpgsql
as
$$
BEGIN
    EXECUTE 'set role openchs;';
    execute 'drop schema "' || in_impl_schema || '" cascade;';
    execute 'delete from entity_sync_status where db_user = ''' || in_db_user || ''';';
    execute 'delete from entity_sync_status where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from index_metadata where table_metadata_id in (select id from table_metadata where schema_name = ''' ||
            in_impl_schema || ''');';
    execute 'delete from column_metadata where table_id in (select id from table_metadata where schema_name = ''' ||
            in_impl_schema || ''');';
    execute 'delete from table_metadata where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from post_etl_sync_status where db_user = ''' || in_db_user || ''';';
    return true;
END
$$;

-- function to archive records from sync_telemetry
CREATE OR REPLACE FUNCTION archive_sync_telemetry(olderthan date) RETURNS bigint
    LANGUAGE plpgsql
AS
$$
DECLARE
    archived_row_count bigint;
BEGIN
    CREATE TABLE IF NOT EXISTS public.sync_telemetry_history
    (
        LIKE public.sync_telemetry INCLUDING ALL
    );
    PERFORM enable_rls_on_tx_table('sync_telemetry_history');
    INSERT INTO public.sync_telemetry_history SELECT * from public.sync_telemetry WHERE sync_start_time < olderthan;
    DELETE FROM public.sync_telemetry WHERE sync_start_time < olderthan;
    GET DIAGNOSTICS archived_row_count = ROW_COUNT;
    RETURN archived_row_count;
END;
$$;

CREATE OR REPLACE FUNCTION grant_all_on_table(rolename text, tablename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE (SELECT 'GRANT ALL ON TABLE '
                        || tablename
                        || ' TO ' || quote_ident(rolename));

    EXECUTE (SELECT 'GRANT SELECT ON '
                        || tablename
                        || ' TO ' || quote_ident(rolename));

    EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO ' || quote_ident(rolename) || '';
    RETURN 'ALL PERMISSIONS GRANTED TO ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION grant_all_on_all(rolename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE (SELECT 'GRANT ALL ON TABLE '
                        || string_agg(format('%I.%I', table_schema, table_name), ',')
                        || ' TO ' || quote_ident(rolename) || ''
             FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_type = 'BASE TABLE');

    EXECUTE (SELECT 'GRANT SELECT ON '
                        || string_agg(format('%I.%I', schemaname, viewname), ',')
                        || ' TO ' || quote_ident(rolename) || ''
             FROM pg_catalog.pg_views
             WHERE schemaname = 'public'
               and viewowner in ('openchs'));

    EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO ' || quote_ident(rolename) || '';
    EXECUTE 'GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO ' || quote_ident(rolename) || '';
    RETURN 'ALL PERMISSIONS GRANTED TO ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION grant_all_on_views(view_names text[], role text)
    RETURNS text AS
$body$
DECLARE
    view_names_string text;
BEGIN
    view_names_string := array_to_string(view_names, ',');
    EXECUTE 'GRANT ALL ON ' || view_names_string || ' TO ' || quote_ident(role) || '';
    RETURN 'EXECUTE GRANT ALL ON ' || view_names_string || ' TO ' || quote_ident(role) || '';
END;
$body$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION revoke_permissions_on_account(rolename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE 'REVOKE ALL ON TABLE account FROM ' || quote_ident(rolename) || '';
    RETURN 'ALL ACCOUNT PERMISSIONS REVOKED FROM ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION multi_select_coded(obs JSONB)
    RETURNS VARCHAR
    LANGUAGE plpgsql
AS
$$
DECLARE
    result VARCHAR;
BEGIN
    BEGIN
        IF JSONB_TYPEOF(obs) = 'array'
        THEN
            SELECT STRING_AGG(C.NAME, ' ,')
            FROM JSONB_ARRAY_ELEMENTS_TEXT(obs) AS OB (UUID)
                     JOIN CONCEPT C ON C.UUID = OB.UUID
            INTO RESULT;
        ELSE
            SELECT SINGLE_SELECT_CODED(obs) INTO RESULT;
        END IF;
        RETURN RESULT;
    EXCEPTION
        WHEN OTHERS
            THEN
                RAISE NOTICE 'Failed while processing multi_select_coded(''%'')', obs :: TEXT;
                RAISE NOTICE '% %', SQLERRM, SQLSTATE;
    END;
END
$$;

CREATE OR REPLACE FUNCTION single_select_coded(obs TEXT)
    RETURNS VARCHAR
    LANGUAGE plpgsql
AS
$$
DECLARE
    result VARCHAR;
BEGIN
    BEGIN
        SELECT name
        FROM concept
        WHERE uuid = obs
        INTO result;
        RETURN result;
    END;
END
$$
    STABLE;

CREATE OR REPLACE FUNCTION single_select_coded(obs JSONB)
    RETURNS VARCHAR
    LANGUAGE plpgsql
AS
$$
DECLARE
    result VARCHAR;
BEGIN
    BEGIN
        IF JSONB_TYPEOF(obs) = 'array'
        THEN
            SELECT name FROM concept WHERE (obs ->> 0) = uuid INTO result;
        ELSEIF JSONB_TYPEOF(obs) = 'string'
        THEN
            select name from concept where (array_to_json(array [obs]) ->> 0) = uuid into result;
        END IF;
        RETURN result;
    END;
END
$$
    STABLE;

CREATE OR REPLACE FUNCTION no_op() RETURNS trigger AS
$$
BEGIN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

create or replace function check_form_mapping_uniqueness(organisationId int, subjectTypeId int, entityId bigint,
                                                         observationsTypeEntityId int, taskTypeId int,
                                                         formId bigint,
                                                         formMappingId int, implVersion int) returns boolean
    language plpgsql
as
$$
declare
begin
    if exists(select *
              from public.form f
              where (
                        (
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
                            and formId = f.id)) then
        raise 'Wrong form type for form id: %', formId;
    end if;

    if exists(select form_mapping.*
              from public.form
                       inner join public.form_mapping on form_mapping.form_id = form.id
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
                and form_mapping.id <> formMappingId) then
        raise 'Duplicate form mapping exists for: organisation_id: %, subject_type_id: %, entity_id: %, observations_type_entity_id: %, task_type_id: %. Using formId: %, formMappingId: %.', organisationId, subjectTypeId, entityId, observationsTypeEntityId, taskTypeId, formId, formMappingId;
    end if;

    return true;
end
$$;

-- SYNC DISABLED CHECKS
create or replace function assert_subject_with_same_sync_disabled(syncDisabled bool, subjectId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise notice 'Checking sync disabled value: %, for subject id: %', syncDisabled, subjectId;
    if exists(select id from public.individual subject where subject.sync_disabled <> syncDisabled and subjectId = subject.id) then
        raise 'Sync disabled value cannot be different from individual. For individual id: %, sync disabled: %',
            subjectId, syncDisabled;
    end if;
    return true;
end
$$;

create or replace function assert_one_of_subjects_with_sync_disabled(syncDisabled bool, subjectId1 bigint, subjectId2 bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    raise notice 'Checking sync disabled value: %, for subject ids: %, %', syncDisabled, subjectId1, subjectId2;
    if (select count(id) from public.individual subject where ((syncDisabled = false and subject.sync_disabled = false) or syncDisabled)
                                                          and subject.id in (subjectId1, subjectId2)) < 2 then
        raise 'Sync can be enabled only if both the subjects have sync enabled. Subject ids: %, %. Sync disabled: %',
            subjectId1, subjectId2, syncDisabled;
    end if;
    return true;
end
$$;

create or replace function checklist_sync_disabled_same_as_individual(syncDisabled bool, programEnrolmentId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    if exists(select subject.id
              from public.individual subject
                       join program_enrolment pe on pe.individual_id = subject.id
              where subject.sync_disabled <> syncDisabled
                and pe.id = programEnrolmentId) then
        raise 'Sync disabled value cannot be different from individual. For program enrolment id: %, sync disabled: %',
            programEnrolmentId, syncDisabled;
    end if;

    return true;
end
$$;

create or replace function checklist_item_sync_disabled_same_as_individual(syncDisabled bool, checklistId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    if exists(select subject.id
              from public.individual subject
                       join program_enrolment pe on pe.individual_id = subject.id
                       join checklist c on c.program_enrolment_id = pe.id
              where subject.sync_disabled <> syncDisabled
                and c.id = checklistId)
    then
        raise 'Sync disabled value cannot be different from individual. For checklist id: %, sync disabled: %',
            checklistId, syncDisabled;
    end if;

    return true;
end
$$;

create or replace function comment_thread_sync_disabled_same_as_individual(syncDisabled bool, commentThreadId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    if exists(select subject.id
              from public.individual subject
                       join comment c on c.subject_id = subject.id
                       join comment_thread ct on ct.id = c.comment_thread_id
              where subject.sync_disabled <> syncDisabled
                and ct.id = commentThreadId)
    then
        raise 'Sync disabled value cannot be different from individual. For comment thread id: %, sync disabled: %',
            commentThreadId, syncDisabled;
    end if;

    return true;
end
$$;

create or replace function program_encounter_sync_disabled_same_as_individual(syncDisabled bool, programEnrolmentId bigint)
    returns boolean
    language plpgsql
as
$$
declare
begin
    if exists(select subject.id
              from public.individual subject
                       join program_enrolment pe on pe.individual_id = subject.id
                       join program_encounter penc on penc.program_enrolment_id = pe.id
              where subject.sync_disabled <> syncDisabled
                and pe.id = programEnrolmentId)
    then
        raise 'Sync disabled value cannot be different from individual. For program enrolment id: %, sync disabled: %',
            programEnrolmentId, syncDisabled;
    end if;

    return true;
end
$$;

-- added line to change checksum
