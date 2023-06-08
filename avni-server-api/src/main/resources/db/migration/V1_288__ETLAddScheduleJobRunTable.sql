create table scheduled_job_run
(
    id            SERIAL PRIMARY KEY,
    job_name      varchar(255)                not null,
    started_at    timestamp(3) with time zone not null,
    ended_at      timestamp(3) with time zone null,
    error_message text                        null
);

-- if exists for dev machine reasons
alter table organisation drop column if exists has_analytics_db;
