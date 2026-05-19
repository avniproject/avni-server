CREATE INDEX IF NOT EXISTS idx_calendar_address_level_id ON calendar (address_level_id);
CREATE INDEX IF NOT EXISTS idx_calendar_last_modified ON calendar (last_modified_date_time);

CREATE INDEX IF NOT EXISTS idx_cdm_calendar_id ON calendar_date_marker (calendar_id);
CREATE INDEX IF NOT EXISTS idx_cdm_last_modified ON calendar_date_marker (last_modified_date_time);

CREATE INDEX IF NOT EXISTS idx_attendance_type_subject_type_id ON attendance_type (subject_type_id);
CREATE INDEX IF NOT EXISTS idx_attendance_type_last_modified ON attendance_type (last_modified_date_time);

CREATE INDEX IF NOT EXISTS idx_session_group_subject_id ON session (group_subject_id);
CREATE INDEX IF NOT EXISTS idx_session_attendance_type_id ON session (attendance_type_id);
CREATE INDEX IF NOT EXISTS idx_session_last_modified ON session (last_modified_date_time);

CREATE INDEX IF NOT EXISTS idx_attendance_record_session_id ON attendance_record (session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_record_subject_id ON attendance_record (subject_id);
CREATE INDEX IF NOT EXISTS idx_attendance_record_last_modified ON attendance_record (last_modified_date_time);
