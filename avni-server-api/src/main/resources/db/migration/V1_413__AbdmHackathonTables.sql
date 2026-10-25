-- DEMO-ONLY abdm-hackathon: ABDM M2 (HIP) and M3 (HIU) state for the NHA hackathon demo. Never merged.
-- Written with JdbcTemplate, so organisation_id is set by the code, not the Hibernate interceptor. Row-level security is
-- the tx variant, own organisation only, as abha_link (V1_411).

create table abdm_message
(
    id              bigserial primary key,
    organisation_id integer      not null references organisation (id),
    direction       varchar(3)   not null,
    method          varchar(10),
    path            varchar(500) not null,
    request_id      varchar(100),
    correlation_id  varchar(100),
    http_status     integer,
    body            jsonb,
    created_at      timestamptz  not null default now()
);
create index abdm_message_org_created on abdm_message (organisation_id, created_at desc);

create table abdm_patient_link
(
    id                     bigserial primary key,
    organisation_id        integer      not null references organisation (id),
    individual_id          bigint       not null references individual (id),
    hip_id                 varchar(100) not null,
    abha_number            varchar(14),
    abha_address           varchar(255),
    status                 varchar(30)  not null,
    token_request_id       varchar(100),
    encrypted_link_token   text,
    link_token_received_at timestamptz,
    error                  text,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now(),
    unique (organisation_id, individual_id, hip_id)
);
create index abdm_patient_link_token_request on abdm_patient_link (token_request_id);

create table abdm_care_context
(
    id                bigserial primary key,
    uuid              varchar(255) not null,
    organisation_id   integer      not null references organisation (id),
    individual_id     bigint       not null references individual (id),
    hip_id            varchar(100) not null,
    patient_reference varchar(255) not null,
    reference         varchar(255) not null,
    display           varchar(500) not null,
    hi_type           varchar(50)  not null,
    status            varchar(30)  not null,
    link_mode         varchar(20),
    link_request_id   varchar(100),
    error             text,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    unique (organisation_id, hip_id, reference)
);
create index abdm_care_context_link_request on abdm_care_context (link_request_id);
create index abdm_care_context_individual on abdm_care_context (individual_id);

create table abdm_link_request
(
    id                bigserial primary key,
    organisation_id   integer      not null references organisation (id),
    transaction_id    varchar(100) not null,
    link_reference    varchar(100) not null,
    individual_id     bigint references individual (id),
    abha_address      varchar(255),
    care_context_refs jsonb,
    status            varchar(30)  not null,
    expires_at        timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);
create index abdm_link_request_ref on abdm_link_request (link_reference);

create table abdm_consent
(
    id                 bigserial primary key,
    uuid               varchar(255) not null,
    organisation_id    integer      not null references organisation (id),
    role               varchar(3)   not null,
    consent_request_id varchar(100),
    consent_id         varchar(100),
    init_request_id    varchar(100),
    fetch_request_id   varchar(100),
    abha_address       varchar(255),
    hip_id             varchar(100),
    hiu_id             varchar(100),
    purpose_code       varchar(50),
    hi_types           text,
    date_from          timestamptz,
    date_to            timestamptz,
    data_erase_at      timestamptz,
    status             varchar(30)  not null,
    consent_detail     jsonb,
    error              text,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);
create index abdm_consent_role_consent on abdm_consent (organisation_id, role, consent_id);
create index abdm_consent_request on abdm_consent (organisation_id, consent_request_id);
create index abdm_consent_init_request on abdm_consent (init_request_id);

create table abdm_data_flow
(
    id                    bigserial primary key,
    uuid                  varchar(255) not null,
    organisation_id       integer      not null references organisation (id),
    role                  varchar(3)   not null,
    consent_id            varchar(100) not null,
    transaction_id        varchar(100),
    request_id            varchar(100),
    data_push_url         varchar(1000),
    encrypted_private_key text,
    public_key            text,
    nonce                 varchar(100),
    key_expiry            timestamptz,
    status                varchar(30)  not null,
    entries               integer,
    error                 text,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now()
);
create index abdm_data_flow_transaction on abdm_data_flow (transaction_id);
create index abdm_data_flow_request on abdm_data_flow (request_id);

create table abdm_health_information
(
    id                     bigserial primary key,
    organisation_id        integer      not null references organisation (id),
    data_flow_id           bigint       not null references abdm_data_flow (id),
    transaction_id         varchar(100) not null,
    consent_id             varchar(100) not null,
    hip_id                 varchar(100),
    care_context_reference varchar(255),
    checksum_matched       boolean,
    fhir_bundle            jsonb        not null,
    received_at            timestamptz  not null default now()
);
create index abdm_health_information_flow on abdm_health_information (data_flow_id);

select enable_rls_on_tx_table('abdm_message');
select enable_rls_on_tx_table('abdm_patient_link');
select enable_rls_on_tx_table('abdm_care_context');
select enable_rls_on_tx_table('abdm_link_request');
select enable_rls_on_tx_table('abdm_consent');
select enable_rls_on_tx_table('abdm_data_flow');
select enable_rls_on_tx_table('abdm_health_information');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_message'), ('abdm_patient_link'), ('abdm_care_context'), ('abdm_link_request'), ('abdm_consent'),
             ('abdm_data_flow'), ('abdm_health_information')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');
