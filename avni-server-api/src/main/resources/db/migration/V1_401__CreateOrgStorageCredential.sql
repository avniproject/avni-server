-- Per-org storage credential store; secret_key is stored encrypted at rest.
create table org_storage_credential
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    credential_ref          varchar(255)                NOT NULL,
    access_key              varchar(1024)               NOT NULL,
    encrypted_secret_key    varchar(2048)               NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table org_storage_credential
    add constraint org_storage_credential_uuid_org_uniq unique (uuid, organisation_id);

create unique index org_storage_credential_org_ref_unique
    on org_storage_credential (organisation_id, credential_ref) where is_voided = false;

select enable_rls_on_ref_table('org_storage_credential');

SELECT grant_all_on_table(a.rolname, 'org_storage_credential')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
