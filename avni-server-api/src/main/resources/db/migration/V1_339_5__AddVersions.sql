alter table organisation_category add column if not exists version int not null default 1;
alter table organisation_status add column if not exists version int not null default 1;
