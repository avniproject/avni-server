alter table users drop constraint if exists users_uuid_org_id_key;
alter table users add unique (uuid);
