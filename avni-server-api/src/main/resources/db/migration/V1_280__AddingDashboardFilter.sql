create table dashboard_filter
(
    id                      SERIAL PRIMARY KEY,
    dashboard_id            integer                     NOT NULL references dashboard (id),
    filter                  jsonb                       not null,
    uuid                    varchar(255)                NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    version                 integer                     not null,
    created_by_id           bigint                      not null references users (id),
    last_modified_by_id     bigint                      not null references users (id),
    created_date_time       timestamp(3) with time zone not null,
    last_modified_date_time timestamp(3) with time zone not null
);

alter table dashboard_filter add unique (uuid, organisation_id);

select enable_rls_on_tx_table('dashboard_filter');
