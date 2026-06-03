create table calendar
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    name                    varchar(255)                NOT NULL,
    working_pattern         jsonb                       NOT NULL DEFAULT '{"mon":"all","tue":"all","wed":"all","thu":"all","fri":"all","sat":"none","sun":"none"}'::jsonb,
    address_level_id        integer                     references address_level (id),
    is_default              boolean                     NOT NULL DEFAULT FALSE,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table calendar
    add constraint calendar_uuid_org_uniq unique (uuid, organisation_id);

create unique index calendar_org_address_level_unique
    on calendar (organisation_id, address_level_id) where is_voided = false;

create unique index calendar_org_global_unique
    on calendar (organisation_id) where address_level_id is null and is_voided = false;

select enable_rls_on_ref_table('calendar');

SELECT grant_all_on_table(a.rolname, 'calendar')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
