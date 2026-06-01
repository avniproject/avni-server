UPDATE attendance_record
   SET last_modified_date_time = now()
 WHERE follow_up_encounter_uuid IS NOT NULL
    OR reason_concept_uuids <> '[]'::jsonb;
