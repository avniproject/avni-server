package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.SubjectTypeRepository;
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
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.when;

public class FastSyncKeyServiceTest {
    private static final Long ORGANISATION_ID = 1L;
    private static final Long USER_ID = 11L;

    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    private FastSyncKeyService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new FastSyncKeyService(subjectTypeRepository);
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(Collections.emptyList());
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(Collections.emptyList());
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

    // Both finders answer for the same org, so the stub cannot pretend a directly assignable
    // subject type exists for one query and not the other.
    private SubjectType orgHasADirectlyAssignableSubjectType(SubjectType... alsoPresent) {
        SubjectType subjectType = new SubjectType();
        subjectType.setType(Subject.Person);
        subjectType.setDirectlyAssignable(true);
        List<SubjectType> all = new java.util.ArrayList<>();
        all.add(subjectType);
        all.addAll(List.of(alsoPresent));
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(all);
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(List.of(subjectType));
        return subjectType;
    }

    @Test
    public void plainLocationScopedUserGetsTheCatchmentKey() {
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithAUserSubjectTypeMakesEveryUserPerUser() {
        // One subject per field worker, joined to that worker's own user_subject row and never
        // scoped by location, so a catchment dump carries every peer's. The restoring device keeps
        // them: it asks syncDetails with includeUserSubjectType=true, so the type is in its own
        // allowlist and clearEntitiesOutsidePrivileges leaves its rows alone.
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(new SubjectType());
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithASubjectTypeDeclaringASyncConceptMakesEveryUserPerUser() {
        // Filtered by each user's own syncAttribute values, which is a per-row distinction, and
        // reconciliation on the device works by entity type. avniproject/avni-client#2153.
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(subjectTypeWithASyncConcept()));
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void orgWhoseSubjectTypesDeclareNoSyncConceptIsNotPerUserOnThatCount() {
        when(subjectTypeRepository.findByIsVoidedFalse()).thenReturn(List.of(new SubjectType(), new SubjectType()));
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void orgWithADirectlyAssignableSubjectTypeGetsTheCatchmentKey() {
        // clearDirectlyAssignedSubjects deletes the uploader's caseload after a restore and resets
        // the affected checkpoints, so the next sync re-pulls only what this user is assigned.
        orgHasADirectlyAssignableSubjectType();
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void aUserSubjectTypeStillCostsTheKeyAlongsideADirectlyAssignableOne() {
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(new SubjectType());
        orgHasADirectlyAssignableSubjectType();
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void aSyncConceptStillCostsTheKeyAlongsideADirectlyAssignableOne() {
        orgHasADirectlyAssignableSubjectType(subjectTypeWithASyncConcept());
        assertTrue(service.isPerUser(aUser()));
    }

    @Test
    public void theKeyIsDecidedWithoutReadingTheUsersGroupMemberships() {
        // Group privileges used to make the key per user. They no longer do:
        // clearEntitiesOutsidePrivileges fetches the restoring user's own syncable items and removes
        // every subject type they do not name. The service is now built from the subject type
        // repository alone, so no group state can reach this decision - that is what this pins, and
        // reinstating the group term cannot satisfy it.
        List<Class<?>> collaborators = java.util.Arrays.stream(FastSyncKeyService.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getType)
                .collect(java.util.stream.Collectors.toList());
        assertEquals(List.of(SubjectTypeRepository.class), collaborators);
        assertFalse(service.isPerUser(aUser()));
    }

    @Test
    public void perUserKeyIsNamespacedByUsername() {
        assertEquals("fastsync/aw@org/fastsync.db", service.perUserKey(aUser()));
    }

    @Test
    public void rejectsAUsernameThatCouldEscapeThePrefix() {
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
