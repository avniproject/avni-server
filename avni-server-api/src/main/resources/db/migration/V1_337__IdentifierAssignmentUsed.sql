alter table identifier_assignment add column used bool not null default false;
update identifier_assignment set used = true, last_modified_date_time = current_timestamp + random() * 5000 * (interval '1 millisecond') where individual_id is not null;
