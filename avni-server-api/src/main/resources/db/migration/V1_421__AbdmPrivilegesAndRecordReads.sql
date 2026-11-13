-- ABDM's own privileges, granted on the User Groups screen; no group is given them here.
-- Not NonTransaction, so an account administrator is not passed them without a grant (PrivilegeRepository.isAllowedForAdmin).
INSERT INTO privilege(uuid, name, entity_type, type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Share ABDM records', 'Abdm', 'ShareAbdmRecords',
        'Link patients'' visits to their ABHA and share their records through ABDM', now(), now(), false),
       (uuid_generate_v4(), 'View ABDM records', 'Abdm', 'ViewAbdmRecords',
        'Ask patients for consent and read the records other providers share through ABDM', now(), now(), false);

-- Every read of received records: who, which consent request, when (AbdmRecordReads).
create table abdm_record_read
(
    id                   bigserial primary key,
    organisation_id      integer      not null references organisation (id),
    user_id              integer      not null references users (id),
    consent_request_uuid varchar(255) not null,
    read_at              timestamptz  not null default now()
);

create index abdm_record_read_consent on abdm_record_read (organisation_id, consent_request_uuid);

select enable_rls_on_tx_table('abdm_record_read');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_record_read')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');
