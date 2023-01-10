create table manual_broadcast_message
(
    id                      SERIAL PRIMARY KEY,
    uuid                    varchar(255)                NOT NULL,
    organisation_id         integer                     NOT NULL references organisation (id),
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    message_template_id     text                        NOT NULL,
    parameters              text[],
    version                 integer                     not null,
    created_by_id           bigint                      not null references users (id),
    last_modified_by_id     bigint                      not null references users (id),
    created_date_time       timestamp(3) with time zone not null,
    last_modified_date_time timestamp(3) with time zone not null
);

alter table manual_broadcast_message
    add unique (uuid, organisation_id);

select enable_rls_on_tx_table('manual_broadcast_message');
