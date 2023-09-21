CREATE INDEX IF NOT EXISTS subject_migration_sync_fields_1_idx ON
    subject_migration(last_modified_date_time, subject_type_id, old_sync_concept_1_value, new_sync_concept_1_value, old_address_level_id, new_address_level_id, organisation_id);

CREATE INDEX IF NOT EXISTS subject_migration_sync_fields_2_idx ON
    subject_migration(last_modified_date_time, subject_type_id, old_address_level_id, new_address_level_id, organisation_id);
