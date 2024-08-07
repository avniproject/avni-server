create table if not exists organisation_category
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

insert into organisation_category (uuid, name, created_date_time, last_modified_date_time, created_by_id, last_modified_by_id)
values ('71e1bf3b-48fb-4d4f-90f3-71c39e15fbf0', 'Production', now(), now(), 1, 1),
       ('95e89458-c152-4557-9929-85f1a275d6a3', 'UAT', now(), now(), 1, 1),
       ('283af4ea-0024-4440-857f-c8a82328a61d', 'Prototype', now(), now(), 1, 1),
       ('f0b0a48d-8d4b-4d13-8956-c1bc577b4971', 'Temporary', now(), now(), 1, 1),
       ('470ecdab-f7be-4336-a52a-1fa280080168', 'Trial', now(), now(), 1, 1),
       ('d75e667e-b7ea-40dd-8d85-1328943d3b65', 'Training', now(), now(), 1, 1),
       ('27eeb3e7-2396-45ac-ba50-b1b50690bcfc', 'Dev', now(), now(), 1, 1);

alter table organisation add column if not exists category_id int null;

update organisation set category_id = organisation_category.id
from organisation_category
where organisation.category = organisation_category.name;

alter table organisation drop column category;

alter table organisation alter column category_id set not null;
