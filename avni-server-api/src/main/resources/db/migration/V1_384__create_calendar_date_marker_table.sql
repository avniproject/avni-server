create table calendar_date_marker
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    calendar_id             integer                     NOT NULL references calendar (id),
    marker_date             date                        NOT NULL,
    name                    varchar(255),
    is_working              boolean                     NOT NULL DEFAULT FALSE,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table calendar_date_marker
    add constraint cdm_uuid_org_uniq unique (uuid, organisation_id);

create unique index cdm_calendar_date_unique
    on calendar_date_marker (calendar_id, marker_date) where is_voided = false;

select enable_rls_on_ref_table('calendar_date_marker');

SELECT grant_all_on_table(a.rolname, 'calendar_date_marker')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
