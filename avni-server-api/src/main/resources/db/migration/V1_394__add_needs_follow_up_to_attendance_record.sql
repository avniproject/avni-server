alter table attendance_record
    add column needs_follow_up boolean NOT NULL DEFAULT FALSE;

-- Backfill so existing follow-up encounters are preserved across the next
-- re-save: any record that already has a linked follow-up was, by the old
-- rule, one that needed follow-up.
update attendance_record
set needs_follow_up = true
where follow_up_encounter_uuid is not null;
