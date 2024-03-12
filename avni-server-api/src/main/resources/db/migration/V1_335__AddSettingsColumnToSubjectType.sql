ALTER TABLE subject_type
  ADD COLUMN settings JSONB;

UPDATE subject_type
  SET settings = ('{
    "displayRegistrationDetails": true,
    "displayPlannedEncounters": true
    }')::jsonb, last_modified_date_time = now();
