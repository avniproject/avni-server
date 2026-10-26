-- avniproject/avni-server#1090 | An ABHA made with a mobile number only has an ABHA address and no number. It is linked
-- too, so that a second patient holding the same address is found. A link holds a number, an address, or both.
-- Numbered 1.415, after the abdm-hackathon branches' 1.413 and 1.414: a database that has those (openchs_eka, and the
-- shared test database) refuses an earlier version where out-of-order is off, as it is in tests.
alter table abha_link alter column abha_number drop not null;
alter table abha_link add constraint abha_link_number_or_address check (abha_number is not null or abha_address is not null);
-- Addresses compare by the part before '@', in any case: an answer may or may not carry its @sbx / @abdm suffix.
create index abha_link_org_address_live on abha_link (organisation_id, split_part(lower(abha_address), '@', 1)) where is_voided = false;
