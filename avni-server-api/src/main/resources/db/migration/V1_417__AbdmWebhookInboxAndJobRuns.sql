-- The Eka webhook inbox. Each verified event is stored before Eka is answered, and a worker on ABDM's own threads
-- handles it as its organisation, trying again after a growing wait up to a cap (AbdmInbox, EkaEventWorker). Rows from
-- before the inbox are events already handled, so they are DONE.
alter table abdm_inbound_event
    add column event_name      varchar(255),
    add column route           varchar(40),
    add column consent_key     varchar(255),
    add column body            text,
    add column status          varchar(20) not null default 'DONE',
    add column attempts        integer     not null default 0,
    add column next_attempt_at timestamptz not null default now(),
    add column claimed_at      timestamptz,
    add column last_error      text,
    add column updated_at      timestamptz not null default now();
alter table abdm_inbound_event alter column status drop default;

create index abdm_inbound_event_due on abdm_inbound_event (next_attempt_at, id) where status = 'RECEIVED';
create index abdm_inbound_event_consent on abdm_inbound_event (organisation_id, consent_key, id)
    where status in ('RECEIVED', 'IN_PROGRESS');
create index abdm_inbound_event_in_progress on abdm_inbound_event (claimed_at) where status = 'IN_PROGRESS';
create index abdm_inbound_event_finished on abdm_inbound_event (organisation_id, received_at) where status in ('DONE', 'FAILED');

-- Each organisation's last success and last error of each ABDM job, for the admin page's status (AbdmJobRuns).
create table abdm_job_run
(
    id              bigserial primary key,
    organisation_id integer     not null references organisation (id),
    task            varchar(50) not null,
    last_success_at timestamptz,
    last_error      text,
    last_error_at   timestamptz,
    unique (organisation_id, task)
);

select enable_rls_on_tx_table('abdm_job_run');

SELECT grant_all_on_table(a.rolname, t.name)
FROM pg_roles a,
     (values ('abdm_job_run')) as t(name)
WHERE pg_has_role('openchs', a.oid, 'member');

-- Eka webhooks are routed by the webhook ID in their address, so Eka settings saved without one get one now: random,
-- opaque, and never changed afterwards (AbhaAdminService keeps it on every later save).
update external_system_config
set config = config || jsonb_build_object('webhookId',
        replace(uuid_generate_v4()::text, '-', '') || replace(uuid_generate_v4()::text, '-', ''))
where system_name = 'Eka'
  and coalesce(config ->> 'webhookId', '') = '';
