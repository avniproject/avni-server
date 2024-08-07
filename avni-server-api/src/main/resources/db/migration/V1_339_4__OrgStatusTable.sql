create table if not exists organisation_status
(
    id                      serial primary key,
    uuid                    varchar(255)                not null,
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    name                    varchar(255)                not null,
    created_date_time       timestamp(3) with time zone not null,
    last_modified_date_time timestamp(3) with time zone not null,
    created_by_id           int                         not null,
    last_modified_by_id     int                         not null
);

insert into organisation_status (uuid, name, created_date_time, last_modified_date_time, created_by_id, last_modified_by_id)
values ('338be2e2-d0e5-4186-b113-b8197ce879c5', 'Live', now(), now(), 1, 1),
       ('7e609db3-ff79-472c-8f28-5b12933faaf5', 'Archived', now(), now(), 1, 1);

alter table organisation add column if not exists status_id int null;

update organisation set status_id = organisation_status.id
from organisation_status
where organisation.status = organisation_status.name;

alter table organisation drop column status;

alter table organisation alter column status_id set not null;
