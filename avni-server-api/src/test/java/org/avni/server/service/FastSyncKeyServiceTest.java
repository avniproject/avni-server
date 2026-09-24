package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FastSyncKeyServiceTest {
    private static final Long ORGANISATION_ID = 1L;
    private static final Long USER_ID = 11L;
    private static final Long EVERYONE_GROUP_ID = 100L;

    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    private FastSyncKeyService service;
    private Group everyone;

    @Before
    public void setUp() {
        initMocks(this);
        service = new FastSyncKeyService(subjectTypeRepository, groupRepository, userGroupRepository);
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(Collections.emptyList());
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(Collections.emptyList());

        everyone = group(Group.Everyone, EVERYONE_GROUP_ID, UUID.randomUUID().toString());
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, ORGANISATION_ID)).thenReturn(everyone);
        memberOf(everyone, group(Group.SQLITE_MIGRATION, 101L, Group.SQLITE_MIGRATION_UUID));
    }

    private User aUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setOrganisationId(ORGANISATION_ID);
        user.setUsername("aw@org");
        return user;
    }

    private SubjectType subjectTypeWithASyncConcept() {
        SubjectType subjectType = new SubjectType();
        subjectType.setSyncRegistrationConcept1Usable(true);
        return subjectType;
    }

    private Group group(String name, Long id, String uuid) {
        Group group = new Group();
        group.setName(name);
        group.setId(id);
        group.setUuid(uuid);
        return group;
    }

    private Group customGroup(String name) {
        return group(name, 200L, UUID.randomUUID().toString());
    }

    private void memberOf(Group... groups) {
        List<UserGroup> memberships = java.util.Arrays.stream(groups)
                .map(group -> UserGroup.createMembership(aUser(), group))
                .collect(java.util.stream.Collectors.toList());
        when(userGroupRepository.findByUser_IdAndIsVoidedFalse(USER_ID)).thenReturn(memberships);
    }

    @Test
    public void plainLocationScopedUserIsNotPerUser() {
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithASubjectTypeDeclaringASyncConceptMakesEveryUserPerUser() {
        // The sync attribute values live per user on that subject type, so the catchment dump is
        // a union of what different users in the catchment may see.
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(subjectTypeWithASyncConcept()));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void orgWhoseSubjectTypesDeclareNoSyncConceptIsNotPerUserOnThatCount() {
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(new SubjectType(), new SubjectType()));
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithADirectlyAssignableSubjectTypeMakesEveryUserPerUser() {
        // Review Focus 3. Org shape is the test, not whether this user holds assignments today.
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(List.of(new SubjectType()));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithAUserSubjectTypeMakesEveryUserPerUser() {
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(new SubjectType());
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void userInOnlyTheBaselineGroupsIsNotPerUser() {
        // This is the case that keeps the shared catchment tier alive. Everyone is attached to every
        // user and cannot be detached, and SQLite Migration only marks the migration, so a user with
        // nothing else holds exactly the baseline privilege set that their catchment peers hold.
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void userInACustomGroupIsPerUser() {
        // SyncDetailsService gates every syncable item on the group privileges of the requesting
        // user, so two users in one catchment sync different entity types.
        memberOf(everyone, customGroup("Field Supervisors"));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void userInAdministratorsIsPerUser() {
        // Administrators is a default group but hasAllPrivileges short-circuits to every privilege,
        // so an admin's dump is a superset of what their catchment peers may see.
        memberOf(everyone, customGroup(Group.Administrators));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void userInMetabaseUsersIsPerUser() {
        memberOf(everyone, customGroup(Group.METABASE_USERS));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void aVoidedMembershipDoesNotMakeTheUserPerUser() {
        verify(userGroupRepository, org.mockito.Mockito.never()).findByUser_IdAndIsVoidedFalse(USER_ID);
        assertFalse(service.isPerUser(aUser()));
        // Voided memberships are excluded by the finder itself, so reading memberships any other way
        // would let a detached custom group keep costing the user the shared dump.
        verify(userGroupRepository).findByUser_IdAndIsVoidedFalse(USER_ID);
    }

    @Test
    public void aCustomGroupRenamedToEveryoneStillMakesTheUserPerUser() {
        // groups is unique on (uuid, organisation_id) only, and updateGroup does not block renaming
        // a group TO a default name, so the baseline cannot be matched by name.
        memberOf(everyone, customGroup(Group.Everyone));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void aCustomGroupRenamedToSqliteMigrationStillMakesTheUserPerUser() {
        memberOf(everyone, customGroup(Group.SQLITE_MIGRATION));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void perUserKeyIsNamespacedByUsername() {
        assertEquals("fastsync/aw@org/fastsync.db", service.perUserKey(aUser()));
    }

    @Test
    public void rejectsAUsernameThatCouldEscapeThePrefix() {
        // Review Focus 2. The segment is interpolated into an S3 key.
        User user = new User();
        user.setUsername("a/../b");
        assertThrows(IllegalArgumentException.class, () -> service.perUserKey(user));
    }

    @Test
    public void safeSegmentRejectsTheSameShapesForAnyCaller() {
        assertThrows(IllegalArgumentException.class, () -> FastSyncKeyService.safeSegment("a/b"));
        assertThrows(IllegalArgumentException.class, () -> FastSyncKeyService.safeSegment("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> FastSyncKeyService.safeSegment(".."));
        assertThrows(IllegalArgumentException.class, () -> FastSyncKeyService.safeSegment(null));
        assertEquals("aw@org", FastSyncKeyService.safeSegment("aw@org"));
    }
}
