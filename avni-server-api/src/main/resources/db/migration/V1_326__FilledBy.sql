alter table encounter add column filled_by_id int null references users(id);
alter table program_encounter add column filled_by_id int null references users(id);
