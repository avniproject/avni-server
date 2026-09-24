package org.avni.server.web;

import org.avni.server.domain.User;
import org.avni.server.service.FastSyncKeyService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MediaControllerFastSyncTest {
    @Mock
    private FastSyncKeyService fastSyncKeyService;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void perUserUserUploadsToTheirOwnKey() {
        User user = new User();
        user.setUsername("aw@org");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(true);
        when(fastSyncKeyService.perUserKey(user)).thenReturn("fastsync/aw@org/fastsync.db");

        assertEquals("fastsync/aw@org/fastsync.db", MediaController.fastSyncUploadKeyFor(user, "cat-uuid", fastSyncKeyService));
    }

    @Test
    public void locationScopedUserUploadsToTheSqliteCatchmentKey() {
        User user = new User();
        user.setUsername("aw@org");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(false);

        assertEquals("MobileDbBackupSqlite-cat-uuid",
                MediaController.fastSyncUploadKeyFor(user, "cat-uuid", fastSyncKeyService));
    }

    @Test
    public void theSqliteCatchmentKeyIsNeverTheRealmOne() {
        User user = new User();
        user.setUsername("aw@org");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(false);

        String key = MediaController.fastSyncUploadKeyFor(user, "cat-uuid", fastSyncKeyService);
        assertEquals("MobileDbBackupSqlite-cat-uuid", key);
        // A SQLite device writing the Realm key would replace the Realm dump and Realm devices
        // would then load a SQLite file into default.realm.
        org.junit.Assert.assertNotEquals("MobileDbBackup-cat-uuid", key);
    }

    @Test
    public void aCatchmentKeyedUploadWithNoCatchmentIsRejected() {
        // Review Focus 1. Without this the key becomes "MobileDbBackupSqlite-null" and every
        // catchmentless user in the organisation shares one object. Same failure the Realm
        // route already produces.
        User user = new User();
        user.setUsername("aw@org");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(false);

        org.avni.server.util.BadRequestError e = org.junit.Assert.assertThrows(
                org.avni.server.util.BadRequestError.class,
                () -> MediaController.fastSyncUploadKeyFor(user, null, fastSyncKeyService));
        assertTrue(e.getMessage().contains("NoCatchmentFound"));
    }

    @Test
    public void aPerUserUserWithNoCatchmentIsFineBecauseTheKeyDoesNotUseIt() {
        User user = new User();
        user.setUsername("aw@org");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(true);
        when(fastSyncKeyService.perUserKey(user)).thenReturn("fastsync/aw@org/fastsync.db");

        assertEquals("fastsync/aw@org/fastsync.db",
                MediaController.fastSyncUploadKeyFor(user, null, fastSyncKeyService));
    }
}
