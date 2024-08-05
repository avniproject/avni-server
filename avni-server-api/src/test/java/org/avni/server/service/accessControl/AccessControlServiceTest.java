package org.avni.server.service.accessControl;

import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.UserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

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
}
