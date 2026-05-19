create table session
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    group_subject_id        bigint                      NOT NULL references individual (id),
    scheduled_date          date                        NOT NULL,
    attendance_type_id      integer                     NOT NULL references attendance_type (id),
    status                  varchar(255)                NOT NULL,
    reason_concept_id       integer                     references concept (id),
    notes                   text,
    marked_by_user_id       bigint                      references users (id),
    marked_at               timestamp(3) with time zone,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table session
    add constraint session_uuid_org_uniq unique (uuid, organisation_id);

create unique index session_group_date_type_unique
    on session (group_subject_id, scheduled_date, attendance_type_id) where is_voided = false;

select enable_rls_on_tx_table('session');

SELECT grant_all_on_table(a.rolname, 'session')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
