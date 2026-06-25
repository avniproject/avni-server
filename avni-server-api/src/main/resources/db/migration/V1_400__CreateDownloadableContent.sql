create table downloadable_content
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    name                    varchar(255)                NOT NULL,
    category                varchar(255)                NOT NULL,
    content_key             varchar(1024),
    sha256                  varchar(255),
    needs_key               boolean                     NOT NULL DEFAULT FALSE,
    payload                 jsonb,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table downloadable_content
    add constraint downloadable_content_uuid_org_uniq unique (uuid, organisation_id);

create unique index downloadable_content_org_name_unique
    on downloadable_content (organisation_id, name) where is_voided = false;

create index idx_downloadable_content_last_modified
    on downloadable_content (last_modified_date_time);

select enable_rls_on_ref_table('downloadable_content');

SELECT grant_all_on_table(a.rolname, 'downloadable_content')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
