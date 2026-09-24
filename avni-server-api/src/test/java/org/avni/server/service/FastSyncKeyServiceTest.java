package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FastSyncKeyServiceTest {
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private GroupRepository groupRepository;
    private FastSyncKeyService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new FastSyncKeyService(subjectTypeRepository, groupRepository);
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(Collections.emptyList());
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(Collections.emptyList());
        when(groupRepository.findByIsVoidedFalse()).thenReturn(Collections.emptyList());
    }

    private User userWithSyncSettings(JsonObject syncSettings) {
        User user = new User();
        user.setUsername("aw@org");
        user.setSyncSettings(syncSettings);
        return user;
    }

    private SubjectType subjectTypeWithASyncConcept() {
        SubjectType subjectType = new SubjectType();
        subjectType.setSyncRegistrationConcept1Usable(true);
        return subjectType;
    }

    private Group group(String name) {
        Group group = new Group();
        group.setName(name);
        return group;
    }

    @Test
    public void plainLocationScopedUserIsNotPerUser() {
        assertFalse(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWithASubjectTypeDeclaringASyncConceptMakesEveryUserPerUser() {
        // The sync attribute values live per user on that subject type, so the catchment dump is
        // a union of what different users in the catchment may see.
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(subjectTypeWithASyncConcept()));
        assertTrue(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWhoseSubjectTypesDeclareNoSyncConceptIsNotPerUserOnThatCount() {
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(new SubjectType(), new SubjectType()));
        assertFalse(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWithADirectlyAssignableSubjectTypeMakesEveryUserPerUser() {
        // Review Focus 3. Org shape is the test, not whether this user holds assignments today.
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(List.of(new SubjectType()));
        assertTrue(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWithAUserSubjectTypeMakesEveryUserPerUser() {
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(new SubjectType());
        assertTrue(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWithACustomGroupMakesEveryUserPerUser() {
        // SyncDetailsService gates every syncable item on the group privileges of the requesting
        // user, so two users in one catchment sync different entity types.
        when(groupRepository.findByIsVoidedFalse()).thenReturn(List.of(group("Field Supervisors")));
        assertTrue(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void orgWithOnlyTheDefaultGroupsIsNotPerUserOnThatCount() {
        // SQLite Migration is a default group: being migrated must not by itself cost every user
        // the shared dump.
        when(groupRepository.findByIsVoidedFalse()).thenReturn(List.of(
                group(Group.Administrators), group(Group.Everyone),
                group(Group.METABASE_USERS), group(Group.SQLITE_MIGRATION)));
        assertFalse(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void perUserKeyIsNamespacedByUsername() {
        assertEquals("fastsync/aw@org/fastsync.db", service.perUserKey(userWithSyncSettings(new JsonObject())));
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
