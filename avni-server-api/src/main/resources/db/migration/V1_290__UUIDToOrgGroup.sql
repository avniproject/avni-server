alter table organisation_group add column uuid CHARACTER VARYING(255)  not null default uuid_generate_v4();
