create table custom_card_config
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    name                    varchar(255)                NOT NULL,
    html_file_s3_key        varchar(1024),
    data_rule               text,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_by_id           bigint                      NOT NULL references users (id),
    last_modified_by_id     bigint                      NOT NULL references users (id),
    created_date_time       timestamp(3) with time zone NOT NULL,
    last_modified_date_time timestamp(3) with time zone NOT NULL,
    version                 integer                     NOT NULL DEFAULT 0
);

alter table custom_card_config
    add constraint custom_card_config_uuid_org_uniq unique (uuid, organisation_id);

create unique index custom_card_config_org_name_unique
    on custom_card_config (organisation_id, name) where is_voided = false;

select enable_rls_on_ref_table('custom_card_config');

SELECT grant_all_on_table(a.rolname, 'custom_card_config')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');

alter table report_card
    add column custom_card_config_id integer references custom_card_config (id);

create index idx_report_card_custom_card_config_id
    on report_card (custom_card_config_id);

alter table report_card drop constraint report_card_optional_standard_report_card_type;
alter table report_card add constraint report_card_exactly_one_type
    check (
        ((standard_report_card_type_id is not null)::int +
         (query is not null)::int +
         (custom_card_config_id is not null)::int) = 1
    );
