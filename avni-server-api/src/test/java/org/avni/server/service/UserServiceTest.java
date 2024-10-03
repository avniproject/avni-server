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

import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserServiceTest {
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private IdpServiceFactory idpServiceFactory;

    @Captor
    ArgumentCaptor<UserGroup> userGroupArgumentCaptor;
    private UserService userService;

    @Before
    public void setup() {
        initMocks(this);

        userService = new UserService(null, groupRepository, userGroupRepository, null, null, null, idpServiceFactory, null);
    }

    @Test
    public void shouldAddToSpecifiedGroupsAndEveryone() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group group = new Group();
        Group everyone = new Group();
        String group1 = "Group 1";
        when(groupRepository.findByNameIgnoreCase(group1)).thenReturn(group);
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);


        userService.addToGroups(user, group1);
        verify(userGroupRepository, times(2)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(user, allValues.get(1).getUser());
        assertEquals(everyone, allValues.get(1).getGroup());
    }

    @Test
    public void shouldAddEveryoneIfNoGroupsSpecified() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group everyone = new Group();
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);

        userService.addToGroups(user, null);
        verify(userGroupRepository, times(1)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(everyone, allValues.get(0).getGroup());
    }

    @Test
    public void shouldDeduplicateListOfGroupsSpecified() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group group1 = new Group();
        Group group2 = new Group();
        Group everyone = new Group();
        when(groupRepository.findByNameIgnoreCase("group1")).thenReturn(group1);
        when(groupRepository.findByNameIgnoreCase("group2")).thenReturn(group2);
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(group1);

        userService.addToGroups(user, "group1,group2,group1");
        verify(userGroupRepository, times(3)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(group1, allValues.get(0).getGroup());
        assertEquals(group2, allValues.get(1).getGroup());
        assertEquals(everyone, allValues.get(2).getGroup());
    }

    @Test
    public void testUserGroupSplitByDelimiter() {
        final String groupsSpecified = "ug1, ug2|ug3 |  ug4-ug5 ,   ug6 ug7";
        String[] userGroups = userService.splitIntoUserGroupNames(groupsSpecified);
        assertArrayEquals(new String[]{"ug1", "ug2", "ug3", "ug4-ug5", "ug6 ug7"}, userGroups);
    }
}
