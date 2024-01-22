insert into privilege(uuid, name, description, type, entity_type, created_date_time, last_modified_date_time)
    values (uuid_generate_v4(), 'Bulk transaction data update', 'Bulk transaction data update of different types', 'MultiTxEntityTypeUpdate', 'BulkAccess', current_timestamp, current_timestamp);
update privilege set entity_type = 'BulkAccess', last_modified_date_time = current_timestamp where entity_type = 'Analytics';
