alter table non_applicable_form_element
  add column is_voided BOOLEAN NOT NULL DEFAULT FALSE;

alter table non_applicable_form_element
  add column version INTEGER NOT NULL default 0;
alter table non_applicable_form_element
  add column created_by_id BIGINT NOT NULL default 1;
alter table non_applicable_form_element
  add column last_modified_by_id BIGINT NOT NULL default 1;
alter table non_applicable_form_element
  add column created_date_time TIMESTAMP NOT NULL default current_timestamp;
alter table non_applicable_form_element
  add column last_modified_date_time TIMESTAMP NOT NULL default current_timestamp;
