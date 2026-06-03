create table attendance_type
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    subject_type_id         integer                     NOT NULL references subject_type (id),
    name                    varchar(255)                NOT NULL,
    sort_order              integer,
    config                  jsonb                       NOT NULL DEFAULT '{}'::jsonb,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table attendance_type
    add constraint attendance_type_uuid_org_uniq unique (uuid, organisation_id);

create unique index attendance_type_subject_type_lower_name_unique
    on attendance_type (subject_type_id, lower(name)) where is_voided = false;

select enable_rls_on_ref_table('attendance_type');

SELECT grant_all_on_table(a.rolname, 'attendance_type')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
