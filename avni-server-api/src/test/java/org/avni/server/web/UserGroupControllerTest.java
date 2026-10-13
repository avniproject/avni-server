package org.avni.server.web;

import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.web.request.UserGroupContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserGroupControllerTest {
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private MetabaseService metabaseService;

    private UserGroupController userGroupController;

    @Before
    public void setUp() {
        initMocks(this);
        Organisation organisation = mock(Organisation.class);
        when(organisation.getName()).thenReturn("test-org");
        UserContext userContext = new UserContext();
        userContext.setOrganisation(organisation);
        UserContextHolder.create(userContext);

        userGroupController = new UserGroupController(userGroupRepository, userRepository, groupRepository,
                accessControlService, organisationConfigService, metabaseService);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void addUsersToGroup_persistsMembershipBeforeSyncingToMetabase() {
        User user = new UserBuilder().withDefaultValuesForNewEntity().build();
        user.setEmail("someone@example.com");
        Group group = new Group();
        group.setName(Group.METABASE_USERS);

        when(userRepository.findOne(1L)).thenReturn(user);
        when(groupRepository.findOne(2L)).thenReturn(group);
        when(userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group)).thenReturn(null);
        when(organisationConfigService.isMetabaseSetupEnabled(any())).thenReturn(true);
        when(userGroupRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        UserGroupContract contract = new UserGroupContract();
        contract.setUserId(1L);
        contract.setGroupId(2L);

        userGroupController.addUsersToGroup(List.of(contract));

        InOrder inOrder = inOrder(userGroupRepository, metabaseService);
        inOrder.verify(userGroupRepository).saveAll(anyList());
        inOrder.verify(metabaseService).upsertUsersOnMetabase(anyList());
    }

    @Test
    public void addUsersToGroup_syncsTheMembershipThatWasActuallySaved() {
        User user = new UserBuilder().withDefaultValuesForNewEntity().build();
        user.setEmail("someone@example.com");
        Group group = new Group();
        group.setName(Group.METABASE_USERS);
        UserGroup existing = UserGroup.createMembership(user, group);

        when(userRepository.findOne(1L)).thenReturn(user);
        when(groupRepository.findOne(2L)).thenReturn(group);
        when(userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group)).thenReturn(existing);
        when(organisationConfigService.isMetabaseSetupEnabled(any())).thenReturn(true);
        when(userGroupRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        UserGroupContract contract = new UserGroupContract();
        contract.setUserId(1L);
        contract.setGroupId(2L);

        userGroupController.addUsersToGroup(List.of(contract));

        verify(metabaseService).upsertUsersOnMetabase(List.of(existing));
    }
}
