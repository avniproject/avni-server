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
            set parameter_type = 'java.util.Date'
            where parameter_type = 'DATE';
            UPDATE batch_job_execution_params bjep
            set parameter_type = 'java.lang.Long'
            where parameter_type = 'LONG';
            UPDATE batch_job_execution_params bjep
            set parameter_type = 'java.lang.Double'
            where parameter_type = 'DOUBLE';
            UPDATE batch_job_execution_params bjep
            set parameter_type = 'java.lang.String'
            where parameter_type = 'STRING';
        END IF;
    END
$$;
