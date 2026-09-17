-- Privileges for creating and editing the Approval and Rejection form types.
--
-- Deliberately no group_privilege insert. V1_389 auto-granted ManageCalendars to every non-admin
-- group on every organisation, which made the privilege a no-op because every user inherited it,
-- and V1_392 had to delete those rows. New privileges are opt-in via the User Groups screen, as
-- with V1_304 (Messaging) and V1_379 (Share*).
--
-- entity_type is NonTransaction, which is what makes these reachable by the Avni admin user
-- without a grant (PrivilegeRepository.isAllowedForAdmin).
INSERT INTO privilege(uuid, name, entity_type, type, description, last_modified_date_time, created_date_time, is_voided)
VALUES (uuid_generate_v4(), 'Edit Approval', 'NonTransaction', 'EditApproval',
        'Ability to create and edit approval forms', now(), now(), false),
       (uuid_generate_v4(), 'Edit Rejection', 'NonTransaction', 'EditRejection',
        'Ability to create and edit rejection forms', now(), now(), false);
