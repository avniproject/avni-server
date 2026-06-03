INSERT INTO privilege (uuid, name, description, entity_type, type, created_date_time, last_modified_date_time, is_voided)
    VALUES (uuid_generate_v4(), 'Share subject', 'Share filled registration form as PDF', 'Subject', 'ShareSubject', current_timestamp, current_timestamp, false);

INSERT INTO privilege (uuid, name, description, entity_type, type, created_date_time, last_modified_date_time, is_voided)
    VALUES (uuid_generate_v4(), 'Share enrolment', 'Share filled enrolment or exit form as PDF', 'Enrolment', 'ShareEnrolment', current_timestamp, current_timestamp, false);

INSERT INTO privilege (uuid, name, description, entity_type, type, created_date_time, last_modified_date_time, is_voided)
    VALUES (uuid_generate_v4(), 'Share encounter', 'Share filled visit or cancel-visit form as PDF', 'Encounter', 'ShareEncounter', current_timestamp, current_timestamp, false);
