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
    private User user;

    @Before
    public void setUp() {
        initMocks(this);
        UserContext userContext = new UserContext();
        userContext.setOrganisation(new Organisation());
        UserContextHolder.create(userContext);

        user = new UserBuilder().id(1L).withDefaultValuesForNewEntity().build();
        when(userRepository.findOne(1L)).thenReturn(user);
        when(organisationConfigService.isMetabaseSetupEnabled(any())).thenReturn(true);
        when(userGroupRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        userGroupController = new UserGroupController(userGroupRepository, userRepository, groupRepository,
                accessControlService, organisationConfigService, metabaseService);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void addUsersToGroup_doesNotCallMetabaseForOtherGroups() {
        when(groupRepository.findOne(2L)).thenReturn(group("Other"));

        userGroupController.addUsersToGroup(List.of(contract()));

        verifyNoInteractions(metabaseService);
    }

    @Test
    public void addUsersToGroup_doesNotCallMetabaseWhenAlreadyInMetabaseUsers() {
        Group metabaseUsers = group(Group.METABASE_USERS);
        when(groupRepository.findOne(2L)).thenReturn(metabaseUsers);
        when(userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, metabaseUsers)).thenReturn(UserGroup.createMembership(user, metabaseUsers));

        userGroupController.addUsersToGroup(List.of(contract()));

        verifyNoInteractions(metabaseService);
    }

    @Test
    public void addUsersToGroup_syncsNewMetabaseUsersMembership() {
        when(groupRepository.findOne(2L)).thenReturn(group(Group.METABASE_USERS));

        userGroupController.addUsersToGroup(List.of(contract()));

        verify(metabaseService).upsertUsersOnMetabase(anyList());
    }

    @Test
    public void removeUserFromGroup_doesNotCallMetabaseWhenAlreadyRemoved() {
        UserGroup userGroup = UserGroup.createMembership(user, group(Group.METABASE_USERS));
        userGroup.setId(5L);
        userGroup.setVoided(true);
        when(userGroupRepository.findOne(5L)).thenReturn(userGroup);

        userGroupController.removeUserFromGroup(5L);

        verifyNoInteractions(metabaseService);
    }

    @Test
    public void removeUserFromGroup_syncsRemovalFromMetabaseUsers() {
        UserGroup userGroup = UserGroup.createMembership(user, group(Group.METABASE_USERS));
        userGroup.setId(5L);
        when(userGroupRepository.findOne(5L)).thenReturn(userGroup);

        userGroupController.removeUserFromGroup(5L);

        verify(metabaseService).upsertUsersOnMetabase(List.of(userGroup));
    }

    private Group group(String name) {
        Group group = new Group();
        group.setId(2L);
        group.setName(name);
        return group;
    }

    private UserGroupContract contract() {
        UserGroupContract contract = new UserGroupContract();
        contract.setUserId(1L);
        contract.setGroupId(2L);
        return contract;
    }
}
