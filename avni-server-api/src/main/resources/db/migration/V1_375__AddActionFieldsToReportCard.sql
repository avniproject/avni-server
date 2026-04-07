ALTER TABLE report_card ADD COLUMN action VARCHAR(255);
UPDATE report_card SET action = 'ViewSubjectProfile', last_modified_date_time = now() WHERE standard_report_card_type_id IS NULL;
