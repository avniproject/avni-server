select create_audit_columns('sync_telemetry');
call update_audit_columns_from_audit_table('sync_telemetry');
commit;
select solidify_audit_columns('sync_telemetry');

alter table sync_telemetry
    drop column id,
    drop column uuid,
    drop column organisationId;

ALTER TABLE sync_telemetry ADD COLUMN created_date_time TIMESTAMP NOT NULL DEFAULT (sync_start_time) ;
ALTER TABLE sync_telemetry ADD COLUMN last_modified_date_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ;
ALTER TABLE sync_telemetry ADD COLUMN created_by_id USER NOT NULL DEFAULT 1;
ALTER TABLE sync_telemetry ADD COLUMN last_modified_by_id USER NOT NULL DEFAULT 1;