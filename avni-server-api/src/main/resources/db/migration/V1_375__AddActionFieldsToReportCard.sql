ALTER TABLE report_card ADD COLUMN action VARCHAR(255) NOT NULL DEFAULT 'ShowSubject';
ALTER TABLE report_card ADD COLUMN action_detail jsonb;
ALTER TABLE report_card ADD COLUMN on_action_completion VARCHAR(255) NOT NULL DEFAULT 'SubjectProfile';
