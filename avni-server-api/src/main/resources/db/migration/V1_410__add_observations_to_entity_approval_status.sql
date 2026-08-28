-- Approval and rejection form answers, stored per decision (#1051).
--
-- Nullable, no default, no backfill. The column has to distinguish three states that a DEFAULT '{}'
-- would flatten into one: no form was filled (NULL), a form was filled and left blank (NULL, see
-- EntityApprovalStatusService), and a form was filled in (the answers). Every client released before
-- the approval/rejection form sends no observations at all and must keep writing NULL.
--
-- Answers live on the decision row rather than on the approved entity so that a second rejection
-- does not overwrite the first one's reasons - each entity_approval_status row keeps its own answers
-- alongside its own status_date_time.
--
-- Shape matches the observations column on the encounter tables: a jsonb object keyed by concept
-- UUID, whose coded values are answer-concept UUIDs, so reports read approval answers exactly as
-- they read visit answers.
alter table entity_approval_status
    add column observations jsonb;
