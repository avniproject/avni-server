-- Add audit fields to existing tables
ALTER TABLE encounter
    ADD COLUMN IF NOT EXISTS created_by_id bigint REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS last_modified_by_id bigint REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS filled_by_id bigint REFERENCES users(id);

ALTER TABLE program_encounter
    ADD COLUMN IF NOT EXISTS created_by_id bigint REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS last_modified_by_id bigint REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS filled_by_id bigint REFERENCES users(id);

-- Create policies for the new audit fields
CREATE POLICY encounter_created_by_id_rls_policy ON encounter
    USING (created_by_id = current_user_id())
    WITH CHECK (created_by_id = current_user_id());
CREATE POLICY encounter_last_modified_by_id_rls_policy ON encounter
    USING (last_modified_by_id = current_user_id())
    WITH CHECK (last_modified_by_id = current_user_id());
CREATE POLICY encounter_filled_by_id_rls_policy ON encounter
    USING (filled_by_id = current_user_id())
    WITH CHECK (filled_by_id = current_user_id());

CREATE INDEX IF NOT EXISTS encounter_created_by_id_idx ON encounter(created_by_id);
CREATE INDEX IF NOT EXISTS encounter_last_modified_by_id_idx ON encounter(last_modified_by_id);
CREATE INDEX IF NOT EXISTS encounter_filled_by_id_idx ON encounter(filled_by_id);

CREATE INDEX IF NOT EXISTS program_encounter_created_by_id_idx ON program_encounter(created_by_id);
CREATE INDEX IF NOT EXISTS program_encounter_last_modified_by_id_idx ON program_encounter(last_modified_by_id);
CREATE INDEX IF NOT EXISTS program_encounter_filled_by_id_idx ON program_encounter(filled_by_id);