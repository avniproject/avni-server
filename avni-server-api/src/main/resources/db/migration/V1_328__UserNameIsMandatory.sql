update users set name = username where name is null;
alter table users alter column name set not null;
