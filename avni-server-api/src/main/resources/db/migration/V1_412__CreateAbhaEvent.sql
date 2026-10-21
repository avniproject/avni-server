-- One row per ABHA action: who created or linked an ABHA, at which health centre, when and with what consent.
-- The phone sends the row when Eka's screens close, so the subject may not have synced yet: no foreign key on subject_uuid.
create table abha_event
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    subject_uuid            varchar(255)                NOT NULL,
    user_id                 bigint                      NOT NULL references users (id),
    hip_id                  varchar(255)                NOT NULL,
    action                  varchar(20)                 NOT NULL,
    route                   varchar(20)                 NOT NULL,
    abha_number             varchar(14),
    abha_address            varchar(255),
    eka_oid                 varchar(255),
    eka_txn_id              varchar(255),
    consent_given_at        timestamp(3) with time zone,
    occurred_at             timestamp(3) with time zone NOT NULL,
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table abha_event
    add constraint abha_event_uuid_org_uniq unique (uuid, organisation_id);

-- Every read is by subject: its latest event, or the user's own latest for it, by the server's receipt time.
create index abha_event_org_subject on abha_event (organisation_id, subject_uuid);

-- Per-patient audit data of one organisation: the transaction policy, so a child organisation cannot read its parent's rows.
select enable_rls_on_tx_table('abha_event');

SELECT grant_all_on_table(a.rolname, 'abha_event')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
