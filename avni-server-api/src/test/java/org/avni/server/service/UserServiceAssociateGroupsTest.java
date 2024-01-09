package org.avni.server.service;

import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.factory.UserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserServiceAssociateGroupsTest {
    public static final boolean NOT_VOIDED = false;
    public static final boolean IS_VOIDED = true;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private GroupRepository groupRepository;
    @Captor
    ArgumentCaptor<UserGroup> userGroupArgumentCaptor;
    @Captor
    ArgumentCaptor<List<UserGroup>> userGroupsToBeSavedArgumentCaptor;

    private UserService userService;
    private Long orgId;
    private Group everyone;
    private Group group1;
    private Group group2;
    private User user;
    private Long[] groupIds;
    UserGroup ug1;
    UserGroup ug2;
    UserGroup ugEveryone;

    @Before
    public void setup() {
        initMocks(this);

        userService = new UserService(null, groupRepository, userGroupRepository);

        // init
        orgId = 1234l;
        user = new UserBuilder().organisationId(orgId).build();
        groupIds = new Long[]{1l, 2l, 3l};
        group1 = initGroup(1l);
        group2 = initGroup(2l);
        everyone = initGroup(3l);
        ug1 = initUserGroup(user, group1);
        ug2 = initUserGroup(user, group2);
        ugEveryone = initUserGroup(user, everyone);
        user.setUserGroups(Arrays.asList(ug1, ug2, ugEveryone));

        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);
        when(groupRepository.findById(1l)).thenReturn(Optional.of(group1));
        when(groupRepository.findById(2l)).thenReturn(Optional.of(group2));
        when(groupRepository.findById(3l)).thenReturn(Optional.of(everyone));
    }

    @Test
    public void handleNullGroupIds() {
        // init
        user.setUserGroups(Arrays.asList(ugEveryone)); // Initially just 3

        groupIds = null; //Group Ids is null

        userService.associateUserToGroups(user, null);

        verify(userGroupRepository, times(0)).saveAll(userGroupsToBeSavedArgumentCaptor.capture());
        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(0, allValues.size());
    }

    @Test
    public void shouldCreateNewUserGroups() {
        // init
        user.setUserGroups(Arrays.asList(ugEveryone)); // Initially just 3

        groupIds = new Long[]{1l, 2l}; //Add 1 and 2

        userService.associateUserToGroups(user, Arrays.asList(groupIds));

        verify(userGroupRepository).saveAll(userGroupsToBeSavedArgumentCaptor.capture());

        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());
        List<UserGroup> userGroupsToSaved = allValues.get(0);

        assertEquals(3, userGroupsToSaved.size());
        validateGroupState(NOT_VOIDED, ug1, userGroupsToSaved, 1l);
        validateGroupState(NOT_VOIDED, ug2, userGroupsToSaved, 2l);
        validateGroupState(NOT_VOIDED, ugEveryone, userGroupsToSaved, 3l);
    }

    @Test
    public void shouldRemoveOldAndCreateNewUserGroup() {
        // init
        user.setUserGroups(Arrays.asList(ug2, ugEveryone)); // Initially, 2 and  3

        groupIds = new Long[]{1l, 3l}; //Add 1 remove 2 retaining 3

        userService.associateUserToGroups(user, Arrays.asList(groupIds));

        verify(userGroupRepository).saveAll(userGroupsToBeSavedArgumentCaptor.capture());

        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());
        List<UserGroup> userGroupsToSaved = allValues.get(0);

        assertEquals(3, userGroupsToSaved.size());
        validateGroupState(NOT_VOIDED, ug1, userGroupsToSaved, 1l);
        validateGroupState(IS_VOIDED, ug2, userGroupsToSaved, 2l);
        validateGroupState(NOT_VOIDED, ugEveryone, userGroupsToSaved, 3l);
    }

    @Test
    public void shouldRemoveTwoGroupsExceptEveryoneUserGroup() {

        // init
        user.setUserGroups(Arrays.asList(ug1, ug2, ugEveryone)); // Initially, all are present

        groupIds = new Long[]{}; //Remove all

        userService.associateUserToGroups(user, Arrays.asList(groupIds));

        verify(userGroupRepository).saveAll(userGroupsToBeSavedArgumentCaptor.capture());

        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());
        List<UserGroup> userGroupsToSaved = allValues.get(0);

        assertEquals(3, userGroupsToSaved.size());
        validateGroupState(IS_VOIDED, ug1, userGroupsToSaved, 1l);
        validateGroupState(IS_VOIDED, ug2, userGroupsToSaved, 2l);
        validateGroupState(NOT_VOIDED, ugEveryone, userGroupsToSaved, 3l);
    }

    @Test
    public void shouldRemoveAllExceptEveryoneUserGroup() {

        // init
        user.setUserGroups(Arrays.asList(ug1, ug2, ugEveryone)); // Initially, all are present

        groupIds = new Long[]{}; //Remove all

        userService.associateUserToGroups(user, Arrays.asList(groupIds));

        verify(userGroupRepository).saveAll(userGroupsToBeSavedArgumentCaptor.capture());

        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());
        List<UserGroup> userGroupsToSaved = allValues.get(0);

        assertEquals(3, userGroupsToSaved.size());
        validateGroupState(IS_VOIDED, ug1, userGroupsToSaved, 1l);
        validateGroupState(IS_VOIDED, ug2, userGroupsToSaved, 2l);
        validateGroupState(NOT_VOIDED, ugEveryone, userGroupsToSaved, 3l);
    }

    @Test
    public void shouldAddEveryoneByDefaultUserGroup() {

        // init
        user.setUserGroups(Arrays.asList(ugEveryone)); // Initially, everyone is present

        groupIds = new Long[]{1l}; //Add only 1

        userService.associateUserToGroups(user, Arrays.asList(groupIds));

        verify(userGroupRepository).saveAll(userGroupsToBeSavedArgumentCaptor.capture());

        List<List<UserGroup>> allValues = userGroupsToBeSavedArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());
        List<UserGroup> userGroupsToSaved = allValues.get(0);

        assertEquals(2, userGroupsToSaved.size());
        validateGroupState(NOT_VOIDED, ug1, userGroupsToSaved, 1l);
        validateGroupState(NOT_VOIDED, ugEveryone, userGroupsToSaved, 3l);
    }

    private void validateGroupState(boolean isPresent, UserGroup targetGrp,
                                    List<UserGroup> userGroupsToSaved, long groupId) {
        Optional<UserGroup> group = findGroup(userGroupsToSaved.stream(), groupId);
        assertTrue(group.isPresent());
        assertEquals(targetGrp.getId(), group.get().getId());
        assertEquals(isPresent, group.get().isVoided());
    }


    private Optional<UserGroup> findGroup(Stream<UserGroup> userGroupsStream, long groupId) {
        return userGroupsStream.filter(ug -> ug.getGroupId() == groupId).findFirst();
    }

    private UserGroup initUserGroup(User user, Group group1) {
        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group1);
        return userGroup;
    }

    private Group initGroup(long id) {
        Group group1 = new Group();
        group1.setId(id);
        return group1;
    }
}
