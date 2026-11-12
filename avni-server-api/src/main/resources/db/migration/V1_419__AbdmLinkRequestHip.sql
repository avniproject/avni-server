-- A patient-led link is confirmed under the HIP ID its code was sent for, so the visits linked are the visits asked for.
alter table abdm_link_request add column hip_id varchar(100);
