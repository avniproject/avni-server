-- V1_389 auto-granted ManageCalendars to every non-admin group on every org,
-- which made the privilege a no-op (every user inherited it). The spec
-- (#1760) says ManageCalendars is opt-in via the User Groups screen, matching
-- how other new privileges (e.g. V1_304 Messaging, V1_379 Share*) were added.
-- Delete the auto-granted rows so admins start from a clean slate.
delete from group_privilege
where privilege_id = (select id from privilege where type = 'ManageCalendars');
