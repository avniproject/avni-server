package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.SubjectTypeRepository;
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
    private FastSyncKeyService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new FastSyncKeyService(subjectTypeRepository);
        when(subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue())
                .thenReturn(Collections.emptyList());
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
    }

    private User userWithSyncSettings(JsonObject syncSettings) {
        User user = new User();
        user.setUsername("aw@org");
        user.setSyncSettings(syncSettings);
        return user;
    }

    @Test
    public void plainLocationScopedUserIsNotPerUser() {
        assertFalse(service.isPerUser(userWithSyncSettings(new JsonObject())));
    }

    @Test
    public void userWithASyncAttributeIsPerUser() {
        JsonObject syncSettings = new JsonObject()
                .with(User.SyncSettingKeys.syncAttribute1.name(), "abc-concept-uuid");
        assertTrue(service.isPerUser(userWithSyncSettings(syncSettings)));
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
}
