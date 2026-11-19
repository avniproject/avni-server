package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.IdpServiceFactory;
import org.avni.server.service.ResetSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.UserContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserControllerTest {
    private static final long ACCOUNT_ID = 1L;

    @Mock private CatchmentRepository catchmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserService userService;
    @Mock private IdpServiceFactory idpServiceFactory;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountAdminRepository accountAdminRepository;
    @Mock private ResetSyncService resetSyncService;
    @Mock private SubjectTypeRepository subjectTypeRepository;
    @Mock private AccessControlService accessControlService;

    private UserController controller;
    private User existingUser;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        controller = new UserController(catchmentRepository, userRepository, organisationRepository, userService,
                idpServiceFactory, accountRepository, accountAdminRepository, resetSyncService, subjectTypeRepository, accessControlService);
        existingUser = new User();
        existingUser.setId(20L);
        existingUser.setUsername("worker@trialorg");
        when(userRepository.findByUsername("worker@trialorg")).thenReturn(existingUser);
        when(userService.getCurrentUser()).thenReturn(new User());
        when(userService.updateUser(any(User.class), anyList(), anyList())).thenAnswer(i -> i.getArgument(0));
        when(userService.createUser(any(User.class), any(), anyList(), anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private void callerIsAnOrganisationAdmin() {
        doThrow(AvniAccessException.createForNotAdmin(new User())).when(accessControlService).assertIsSuperAdmin();
    }

    @Test
    public void organisationAdminCannotCreateAnAccountAdmin() throws Exception {
        callerIsAnOrganisationAdmin();
        try {
            controller.createUser(aUser("new@trialorg", Collections.singletonList(ACCOUNT_ID)));
            fail("EditUserConfiguration alone must not grant account admin");
        } catch (AvniAccessException expected) {
        }
        verify(userService, never()).createUser(any(), any(), anyList(), anyList());
    }

    @Test
    public void organisationAdminCannotMakeAUserAnAccountAdmin() {
        callerIsAnOrganisationAdmin();
        try {
            controller.updateUser(aUser("worker@trialorg", Collections.singletonList(ACCOUNT_ID)), 20L);
            fail("an organisation admin must not be able to promote anyone, themselves included, to account admin");
        } catch (AvniAccessException expected) {
        }
        verify(userService, never()).updateUser(any(), anyList(), anyList());
    }

    @Test
    public void organisationAdminCannotEditAnAccountAdmin() {
        callerIsAnOrganisationAdmin();
        when(userService.isAdmin(existingUser)).thenReturn(true);
        try {
            controller.updateUser(aUser("worker@trialorg", Collections.emptyList()), 20L);
            fail("an organisation admin must not be able to edit or demote Avni staff");
        } catch (AvniAccessException expected) {
        }
        verify(userService, never()).updateUser(any(), anyList(), anyList());
        verifyNoInteractions(idpServiceFactory);
    }

    @Test
    public void organisationAdminCanStillCreateAndEditOrdinaryUsers() throws Exception {
        callerIsAnOrganisationAdmin();

        controller.createUser(aUser("new@trialorg", Collections.emptyList()));
        controller.updateUser(aUser("worker@trialorg", Collections.emptyList()), 20L);

        verify(userService).createUser(any(User.class), any(), eq(Collections.emptyList()), anyList());
        verify(userService).updateUser(same(existingUser), eq(Collections.emptyList()), anyList());
        verify(accessControlService, never()).assertIsSuperAdmin();
    }

    @Test
    public void superAdminCanGrantAccountAdmin() throws Exception {
        controller.createUser(aUser("new@trialorg", Collections.singletonList(ACCOUNT_ID)));
        controller.updateUser(aUser("worker@trialorg", Collections.singletonList(ACCOUNT_ID)), 20L);

        verify(accessControlService, times(2)).checkPrivilege(PrivilegeType.EditUserConfiguration);
        verify(accessControlService, times(2)).assertIsSuperAdmin();
        verify(userService).createUser(any(User.class), any(), eq(Collections.singletonList(ACCOUNT_ID)), anyList());
        verify(userService).updateUser(same(existingUser), eq(Collections.singletonList(ACCOUNT_ID)), anyList());
    }

    private static UserContract aUser(String username, List<Long> accountIds) {
        UserContract contract = new UserContract();
        contract.setUsername(username);
        contract.setName("A User");
        contract.setEmail("a.user@example.org");
        contract.setPhoneNumber("9876543210");
        contract.setRegion("IN");
        contract.setOrganisationId(2L);
        contract.setOperatingIndividualScope("None");
        contract.setAccountIds(accountIds);
        contract.setGroupIds(Collections.emptyList());
        return contract;
    }
}
