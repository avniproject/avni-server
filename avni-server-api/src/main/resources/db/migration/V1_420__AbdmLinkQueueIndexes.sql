-- The link job's settling reads each HIP ID's waiting visits, those waiting for a verified ABHA among them (a removed
-- patient's or visit's are given up), so the waiting-visits index covers that status too.
drop index if exists abdm_care_context_waiting;
create index abdm_care_context_unsettled on abdm_care_context (organisation_id, hip_id, updated_at)
    where status in ('QUEUED', 'FAILED', 'LINK_REQUESTED', 'AWAITING_VERIFIED_ABHA');

-- One Eka ID belongs to one live subject: a second claim is found by the organisation and the Eka ID
-- (AbhaEventRepository.findOtherLiveSubjectsNamingEkaOid).
create index abha_event_org_eka_oid on abha_event (organisation_id, eka_oid) where eka_oid is not null;
