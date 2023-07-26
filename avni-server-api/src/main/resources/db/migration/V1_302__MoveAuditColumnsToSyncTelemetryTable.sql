alter table sync_telemetry
    add column created_by_id bigint,
    add column last_modified_by_id     bigint,
    add column is_voided               boolean not null default false,
    add column created_date_time       timestamp(3) with time zone,
    add column last_modified_date_time timestamp(3) with time zone;

update sync_telemetry
set created_date_time       = COALESCE(sync_end_time, sync_start_time),
    last_modified_date_time = coalesce(sync_end_time, sync_start_time),
    created_by_id           = user_id,
    last_modified_by_id     = user_id;

commit;
alter table audit
    alter column created_by_id set not null,
alter
column last_modified_by_id set not null,
    alter
column created_date_time set not null,
    alter
column last_modified_date_time set not null;
