UPDATE standard_report_card_type
SET name = 'Recent registrations', description = 'Recent registrations', last_modified_date_time = current_timestamp
  WHERE name = 'Last 24 hours registrations';
UPDATE standard_report_card_type
SET name = 'Recent enrolments', description = 'Recent enrolments', last_modified_date_time = current_timestamp
  WHERE name = 'Last 24 hours enrolments';
UPDATE standard_report_card_type
SET name = 'Recent visits', description = 'Recent visits', last_modified_date_time = current_timestamp
  WHERE name = 'Last 24 hours visits';
