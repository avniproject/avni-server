-- avniproject/avni-server#1090 | Which subject holds which ABHA number, so that asking who holds a number is an index
-- read at any organisation size, never a scan of every subject's observations.
--
-- Kept up to date by IndividualService.save and voidSubject, only in organisations with enableAbha on. Not synced,
-- not in ETL. abha_number is digits only. A subject has at most one live row.
--
-- Row-level security is the tx variant, own organisation only, as attendance_record (V1_388): a row is about one
-- patient. The ref variant would let a child organisation read its parent's rows.
create table abha_link
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    individual_id           bigint                      NOT NULL references individual (id),
    abha_number             varchar(14)                 NOT NULL,
    abha_address            varchar(255),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table abha_link
    add constraint abha_link_uuid_org_uniq unique (uuid, organisation_id);

create unique index abha_link_individual_live
    on abha_link (individual_id) where is_voided = false;

create index abha_link_org_number_live
    on abha_link (organisation_id, abha_number) where is_voided = false;

select enable_rls_on_tx_table('abha_link');

SELECT grant_all_on_table(a.rolname, 'abha_link')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
