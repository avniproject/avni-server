ALTER TABLE group_subject ADD COLUMN removal_reason_concept_uuid TEXT;

CREATE INDEX idx_group_subject_removal_reason_concept_uuid
    ON group_subject(removal_reason_concept_uuid)
    WHERE removal_reason_concept_uuid IS NOT NULL;
