ALTER TABLE attendance_record ADD COLUMN reason_concept_uuids jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE attendance_record ar
   SET reason_concept_uuids = jsonb_build_array(c.uuid)
  FROM concept c
 WHERE ar.reason_concept_id = c.id;

ALTER TABLE attendance_record DROP COLUMN reason_concept_id;
