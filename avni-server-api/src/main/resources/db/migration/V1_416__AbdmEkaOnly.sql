-- DEMO-ONLY abdm-hackathon: never merged. ABDM M2 and M3 go through Eka Care only (27 Oct 2026, avni-product-ops
-- analysis/abdm-compliance/abdm-compliance-fixes.md). What only the direct route to NHA's gateway used goes: its link
-- tokens, the HIP's own consent rows (written only by NHA's consent notify), and the columns and index nothing reads now.
-- To be folded into one migration with V1_413 and V1_414 when ABDM is made ready for a release.

drop table if exists abdm_patient_link;

delete from abdm_consent where role = 'HIP';
alter table abdm_consent drop column if exists fetch_request_id;
alter table abdm_consent drop column if exists consent_detail;

drop index if exists abdm_data_flow_request;

alter table abdm_message drop column if exists correlation_id;
