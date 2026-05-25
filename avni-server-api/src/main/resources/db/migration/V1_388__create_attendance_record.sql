create table attendance_record
(
    id                       SERIAL PRIMARY KEY,
    uuid                     varchar(255)                NOT NULL,
    session_id               integer                     NOT NULL references session (id),
    subject_id               bigint                      NOT NULL references individual (id),
    status                   varchar(255)                NOT NULL,
    reason_concept_id        integer                     references concept (id),
    follow_up_encounter_uuid text,
    organisation_id          integer                     NOT NULL references organisation (id),
    is_voided                boolean                     NOT NULL DEFAULT FALSE,
    created_by_id            bigint                      NOT NULL references users (id),
    last_modified_by_id      bigint                      NOT NULL references users (id),
    created_date_time        timestamp(3) with time zone NOT NULL,
    last_modified_date_time  timestamp(3) with time zone NOT NULL,
    version                  integer                     NOT NULL DEFAULT 0
);

alter table attendance_record
    add constraint attendance_record_uuid_org_uniq unique (uuid, organisation_id);

create unique index attendance_record_session_subject_unique
    on attendance_record (session_id, subject_id) where is_voided = false;

select enable_rls_on_tx_table('attendance_record');

SELECT grant_all_on_table(a.rolname, 'attendance_record')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
