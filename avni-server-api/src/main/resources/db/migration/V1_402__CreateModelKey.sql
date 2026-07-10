-- Server-only model key store; holds the model's AES key encrypted at rest, keyed by org + sha256.
create table model_key
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    sha256                  varchar(255)                NOT NULL,
    encrypted_key           varchar(2048)               NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table model_key
    add constraint model_key_uuid_org_uniq unique (uuid, organisation_id);

create unique index model_key_org_sha256_unique
    on model_key (organisation_id, sha256) where is_voided = false;

select enable_rls_on_ref_table('model_key');

SELECT grant_all_on_table(a.rolname, 'model_key')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
