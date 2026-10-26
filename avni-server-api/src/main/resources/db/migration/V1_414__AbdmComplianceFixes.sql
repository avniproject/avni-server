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
