UPDATE standard_report_card_type
SET name = 'Last 24 hours registrations', description = 'Recent registrations', last_modified_date_time = current_timestamp
WHERE name = 'Recent registrations';
UPDATE standard_report_card_type
SET name = 'Last 24 hours enrolments', description = 'Recent enrolments', last_modified_date_time = current_timestamp
WHERE name = 'Recent enrolments';
UPDATE standard_report_card_type
SET name = 'Last 24 hours visits', description = 'Recent visits', last_modified_date_time = current_timestamp
WHERE name = 'Recent visits';

alter table standard_report_card_type add column type varchar(100) null;
update standard_report_card_type set type =  replace(initcap(description), ' ', '') where 1 = 1;
alter table standard_report_card_type alter column type set not null;
