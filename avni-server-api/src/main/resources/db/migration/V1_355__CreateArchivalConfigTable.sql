CREATE TABLE archival_config
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    sql_query               text                        NOT NULL,
    realm_query             text                        NOT NULL,
    batch_size              integer                     NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      not null references users (id),
    last_modified_by_id     bigint                      not null references users (id),
    created_date_time       timestamp(3) with time zone not null,
    last_modified_date_time timestamp(3) with time zone not null,
    version                 bigint                      not null default 0
);

alter table archival_config
    add unique (uuid, organisation_id);

grant all on table archival_config to public;
grant all on sequence archival_config_id_seq to public;

SELECT enable_rls_on_ref_table('archival_config');
