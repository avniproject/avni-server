DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'batch_job_execution_params') THEN
            create index idx_batch_job_execution_params_parameter_name on batch_job_execution_params (parameter_name);
            create index idx_batch_job_execution_params_parameter_value on batch_job_execution_params (parameter_value);
        END IF;
    END
$$;
