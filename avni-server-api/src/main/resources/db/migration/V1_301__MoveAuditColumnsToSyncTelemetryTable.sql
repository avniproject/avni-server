select create_audit_columns('sync_telemetry');

update sync_telemetry set created_date_time = COALESCE(sync_end_time,sync_start_time),
                          last_modified_date_time = coalesce(sync_end_time,sync_start_time),
                          created_by_id = user_id,
                          last_modified_by_id = user_id;

commit;
select solidify_audit_columns('sync_telemetry');

