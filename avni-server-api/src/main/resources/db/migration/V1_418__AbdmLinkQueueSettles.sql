-- The visit link queue settles (AbdmHousekeepingJob.linkQueuedVisits): a try that may pass later waits longer each time
-- and is given up at a cap as FAILED_FINAL. attempts counts the tries that did not settle the link.
alter table abdm_care_context
    add column attempts        integer not null default 0,
    add column next_attempt_at timestamptz;

-- A failure kept from before waits the hour it was promised.
update abdm_care_context set next_attempt_at = updated_at + interval '1 hour' where status = 'FAILED';

-- The job takes each organisation's waiting visits under one HIP ID, longest waiting first.
create index abdm_care_context_waiting on abdm_care_context (organisation_id, hip_id, updated_at)
    where status in ('QUEUED', 'FAILED', 'LINK_REQUESTED');
