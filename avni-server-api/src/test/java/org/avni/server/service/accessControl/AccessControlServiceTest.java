package org.avni.server.service.accessControl;

import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.SubjectPartitionCheckStatus;
import org.avni.server.service.UserService;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.UserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AccessControlServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrivilegeRepository privilegeRepository;

    @Before
    public void setup() {
        initMocks(this);
        when(userRepository.hasAllPrivileges(1L)).thenReturn(false);
    }

    @Test
    public void adminHasPrivilegeForNonTxn() {
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(1L).isAdmin(true).build();
        when(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditSubjectType)).thenReturn(true);
        accessControlService.checkPrivilege(user, PrivilegeType.EditSubjectType);
    }

    @Test(expected = AvniAccessException.class)
    public void adminDoesntHavePrivilegeTxn() {
        initMocks(this);
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(1L).isAdmin(true).build();
        when(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditSubject)).thenReturn(false);
        accessControlService.checkPrivilege(user, PrivilegeType.EditSubject);
    }

    // EditApproval / EditRejection (#1050). These are NonTransaction privileges granted to nobody by
    // the migration, so who may build approval and rejection forms is decided entirely here.

    @Test
    public void adminCanEditApprovalAndRejectionFormsWithoutAGrant() {
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(1L).isAdmin(true).build();
        when(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditApproval)).thenReturn(true);
        when(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditRejection)).thenReturn(true);
        accessControlService.checkPrivilege(user, PrivilegeType.EditApproval);
        accessControlService.checkPrivilege(user, PrivilegeType.EditRejection);
    }

    @Test
    public void aUserGroupWithAllPrivilegesCanEditApprovalFormsWithoutAGrant() {
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(2L).isAdmin(false).build();
        when(userRepository.hasAllPrivileges(2L)).thenReturn(true);
        accessControlService.checkPrivilege(user, PrivilegeType.EditApproval);
    }

    /**
     * Note the verify: stubbing hasPrivilege(..) to false would only restate Mockito's default for
     * boolean, so on its own it configures nothing and the test would still pass if the code checked
     * EditRejection, an unrelated privilege, or never called hasPrivilege at all. The verify is what
     * pins that the right privilege is the one being checked.
     */
    @Test
    public void anOrdinaryUserWithoutTheGrantIsRefusedApprovalFormEditing() {
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(1L).isAdmin(false).build();

        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.EditApproval));

        verify(userRepository).hasPrivilege(PrivilegeType.EditApproval.name(), 1L);
    }

    @Test
    public void anOrdinaryUserHoldingTheGrantMayEditApprovalForms() {
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, null);
        User user = new UserBuilder().id(1L).isAdmin(false).build();
        when(userRepository.hasPrivilege(PrivilegeType.EditApproval.name(), 1L)).thenReturn(true);

        accessControlService.checkPrivilege(user, PrivilegeType.EditApproval);

        verify(userRepository).hasPrivilege(PrivilegeType.EditApproval.name(), 1L);
    }

    @Test
    public void userWithNullSyncSettingsIsToldSyncAttributeNotConfiguredInsteadOfFailing() {
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        AccessControlService accessControlService = new AccessControlService(userRepository, null, null, null, privilegeRepository, null, null, userService);
        User user = new UserBuilder().id(2L).build();
        user.setCatchment(new Catchment());
        user.setSyncSettings(null);
        when(userService.getCurrentUser()).thenReturn(user);
        SubjectType subjectType = new SubjectType();
        subjectType.setUuid("st-uuid");
        subjectType.setSyncRegistrationConcept1Usable(true);
        Individual subject = new Individual();
        subject.setSubjectType(subjectType);
        subject.setAddressLevel(new org.avni.server.domain.AddressLevel());

        SubjectPartitionCheckStatus status = accessControlService.checkSubjectAccess(subject, null);

        assertEquals(SubjectPartitionCheckStatus.UserSyncAttributeNotConfigured, status.getMessage());
    }
}
