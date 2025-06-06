DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1
             FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name = 'batch_job_execution_params'
            )
        THEN
            UPDATE batch_job_execution_params bjep
            set string_val = long_val::text
            where type_cd = 'LONG';
            UPDATE batch_job_execution_params bjep
            set string_val = date_val::text
            where type_cd = 'DATE';
            UPDATE batch_job_execution_params bjep
            set string_val = double_val::text
            where type_cd = 'DOUBLE';
        END IF;
    END
$$;

ALTER TABLE if exists BATCH_STEP_EXECUTION
    ADD CREATE_TIME TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00';

ALTER TABLE if exists BATCH_STEP_EXECUTION
    ALTER COLUMN START_TIME DROP NOT NULL;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS
    DROP COLUMN DATE_VAL;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS
    DROP COLUMN LONG_VAL;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS
    DROP COLUMN DOUBLE_VAL;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS ALTER COLUMN TYPE_CD TYPE VARCHAR(100);

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS RENAME TYPE_CD TO PARAMETER_TYPE;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS ALTER COLUMN KEY_NAME TYPE VARCHAR(100);

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS RENAME KEY_NAME TO PARAMETER_NAME;

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS ALTER COLUMN STRING_VAL TYPE VARCHAR(2500);

ALTER TABLE if exists BATCH_JOB_EXECUTION_PARAMS RENAME STRING_VAL TO PARAMETER_VALUE;
