update form_element_group set name = display, last_modified_date_time = current_timestamp + random() * 5000 * (interval '1 millisecond') where display is not null and display <> '' and name <> display;
ALTER TABLE form_element_group DROP COLUMN display;
