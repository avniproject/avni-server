-- When an ABDM job failing for the organisation last went to Bugsnag: the first failure after a success does, then at
-- most one an hour while it keeps failing (AbdmJobRuns.failed). Every failure is still kept for the status page.
alter table abdm_job_run add column last_alerted_at timestamptz;
