-- DEMO-ONLY abdm-hackathon: fixes from the 26 Oct 2026 NHA compliance run (avni-product-ops
-- analysis/abdm-compliance/abdm-compliance-fixes.md). Never merged. Same conventions as V1_413: plain JDBC, organisation_id
-- set by the code, row-level security on every table.

-- An event that names something we cannot place yet (a consent update for a request not yet matched to ABDM's id). Kept
-- until it can be applied; never guessed onto another row. detail carries ids and statuses only, never a patient identifier.
create table abdm_parked_event
(
    id              bigserial primary key,
    organisation_id integer      not null references organisation (id),
    kind            varchar(50)  not null,
    reference       varchar(100) not null,
    status          varchar(30),
    detail          jsonb,
    created_at      timestamptz  not null default now(),
    resolved_at     timestamptz
);
create index abdm_parked_event_reference on abdm_parked_event (organisation_id, kind, reference) where resolved_at is null;

select enable_rls_on_tx_table('abdm_parked_event');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_parked_event')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');

-- Inbound events already taken, so a webhook delivered again (before or after a restart) has one effect (AVNI-15).
create table abdm_inbound_event
(
    id              bigserial primary key,
    organisation_id integer      not null references organisation (id),
    source          varchar(20)  not null,
    event_id        varchar(100) not null,
    received_at     timestamptz  not null default now(),
    unique (organisation_id, source, event_id)
);

select enable_rls_on_tx_table('abdm_inbound_event');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_inbound_event')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');

-- Each status a consent request or artefact took, and when (HIU_FLOW_104). Keyed by the abdm_consent row, so the history
-- survives a request's id being mapped from Eka's to ABDM's.
create table abdm_consent_status
(
    id              bigserial primary key,
    organisation_id integer     not null references organisation (id),
    consent_row_id  bigint      not null references abdm_consent (id) on delete cascade,
    status          varchar(30) not null,
    changed_at      timestamptz not null default now()
);
create index abdm_consent_status_row on abdm_consent_status (consent_row_id);

select enable_rls_on_tx_table('abdm_consent_status');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_consent_status')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');

-- Received records are kept encrypted (AVNI-14). Rows from before this migration keep their plaintext until their
-- consent ends and the hourly erase deletes them.
alter table abdm_health_information alter column fhir_bundle drop not null;
alter table abdm_health_information add column fhir_bundle_encrypted text;

-- Patient-led linking through Eka: wrong codes are counted, and a request is closed after three (USER_INIT_LINK_605).
alter table abdm_link_request add column attempts integer not null default 0;
