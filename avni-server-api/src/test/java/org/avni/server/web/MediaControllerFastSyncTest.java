package org.avni.server.web;

import org.avni.server.domain.User;
import org.avni.server.web.response.FastSyncDownloadResponse;
import org.avni.server.web.response.FastSyncTier;
import org.avni.server.service.FastSyncKeyService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    private java.util.Optional<String> resolve(User user, boolean perUser, java.util.Set<String> present) {
        return resolve(user, perUser, "cat-uuid", present);
    }

    private java.util.Optional<String> resolve(User user, boolean perUser, String catchmentUuid,
                                               java.util.Set<String> present) {
        return resolveArtifact(user, perUser, catchmentUuid, present)
                .map(MediaController.FastSyncArtifact::key);
    }

    private java.util.Optional<MediaController.FastSyncArtifact> resolveArtifact(
            User user, boolean perUser, String catchmentUuid, java.util.Set<String> present) {
        when(fastSyncKeyService.isPerUser(user)).thenReturn(perUser);
        when(fastSyncKeyService.perUserKey(user)).thenReturn("fastsync/aw@org/fastsync.db");
        return MediaController.fastSyncDownloadKeyFor(user, catchmentUuid, fastSyncKeyService, present::contains);
    }

    private FastSyncTier tierOf(User user, boolean perUser, java.util.Set<String> present) {
        return resolveArtifact(user, perUser, "cat-uuid", present).orElseThrow().tier();
    }

    @Test
    public void theTierSerialisesWithTheExactSpellingTheClientBranchesOn() throws Exception {
        // The client switches restore behaviour on these three strings. Renaming an enum constant
        // must not change what goes on the wire, which is what @JsonValue is there to guarantee.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("\"perUser\"", mapper.writeValueAsString(FastSyncTier.PER_USER));
        assertEquals("\"catchment\"", mapper.writeValueAsString(FastSyncTier.CATCHMENT));
        assertEquals("\"snapshot\"", mapper.writeValueAsString(FastSyncTier.SNAPSHOT));
        assertEquals("{\"url\":\"https://s3/get\",\"tier\":\"catchment\"}",
                mapper.writeValueAsString(new FastSyncDownloadResponse("https://s3/get", FastSyncTier.CATCHMENT)));
    }

    @Test
    public void theArtifactCarriesTheTierOfTheKeyThatActuallyWon() {
        // The client picks assert-identity vs overwrite-identity from this, so the tier is bound to
        // the key when the candidate is built rather than recomputed afterwards, where it could drift.
        assertEquals(FastSyncTier.PER_USER,
                tierOf(aUser(), true, java.util.Set.of("fastsync/aw@org/fastsync.db")));
        assertEquals(FastSyncTier.CATCHMENT,
                tierOf(aUser(), false, java.util.Set.of("MobileDbBackupSqlite-cat-uuid")));
        assertEquals(FastSyncTier.SNAPSHOT,
                tierOf(aUser(), true, java.util.Set.of("snapshots/aw@org/snapshot.db")));
    }

    @Test
    public void aPerUserUserFallingThroughToTheSnapshotIsTaggedSnapshotNotPerUser() {
        // The dangerous confusion: the client must not overwrite identity on a snapshot, nor assert
        // it on a catchment dump. Falling through must change the tier, not just the key.
        assertEquals(FastSyncTier.SNAPSHOT,
                tierOf(aUser(), true, java.util.Set.of("snapshots/aw@org/snapshot.db")));
        assertEquals(FastSyncTier.SNAPSHOT,
                tierOf(aUser(), false, java.util.Set.of("snapshots/aw@org/snapshot.db")));
    }

    private User aUser() {
        User user = new User();
        user.setUsername("aw@org");
        return user;
    }

    @Test
    public void perUserUserPrefersTheirOwnUploadOverTheGeneratedSnapshot() {
        java.util.Set<String> present = java.util.Set.of(
                "fastsync/aw@org/fastsync.db", "snapshots/aw@org/snapshot.db");
        assertEquals(java.util.Optional.of("fastsync/aw@org/fastsync.db"), resolve(aUser(), true, present));
    }

    @Test
    public void perUserUserFallsBackToTheGeneratedSnapshot() {
        assertEquals(java.util.Optional.of("snapshots/aw@org/snapshot.db"),
                resolve(aUser(), true, java.util.Set.of("snapshots/aw@org/snapshot.db")));
    }

    @Test
    public void perUserUserIsNeverOfferedTheSharedCatchmentDump() {
        // Falling back to the catchment union would reopen #956 by a different route.
        assertEquals(java.util.Optional.empty(),
                resolve(aUser(), true, java.util.Set.of("MobileDbBackupSqlite-cat-uuid")));
    }

    @Test
    public void locationScopedUserPrefersTheCatchmentDumpOverTheGeneratedSnapshot() {
        java.util.Set<String> present = java.util.Set.of(
                "MobileDbBackupSqlite-cat-uuid", "snapshots/aw@org/snapshot.db");
        assertEquals(java.util.Optional.of("MobileDbBackupSqlite-cat-uuid"), resolve(aUser(), false, present));
    }

    @Test
    public void locationScopedUserFallsBackToTheGeneratedSnapshot() {
        assertEquals(java.util.Optional.of("snapshots/aw@org/snapshot.db"),
                resolve(aUser(), false, java.util.Set.of("snapshots/aw@org/snapshot.db")));
    }

    @Test
    public void nothingPresentResolvesToEmptySoTheClientDoesAFullSync() {
        assertEquals(java.util.Optional.empty(), resolve(aUser(), false, java.util.Set.of()));
    }

    @Test
    public void theRealmDumpIsNeverResolvedForASqliteUser() {
        assertEquals(java.util.Optional.empty(),
                resolve(aUser(), false, java.util.Set.of("MobileDbBackup-cat-uuid")));
    }

    @Test
    public void aUserOutsideTheMigrationGroupIsNotEligibleEvenWhenAnArtifactExists() {
        // Review Focus 4. The group gate is what makes "removed from the SQLite group" work: the
        // client must get false here so it falls through to the Realm path, not someone's SQLite file.
        assertFalse(MediaController.fastSyncEligible(false, java.util.Optional.of(new MediaController.FastSyncArtifact("fastsync/aw@org/fastsync.db", FastSyncTier.PER_USER))));
    }

    @Test
    public void aGroupMemberWithAnArtifactIsEligible() {
        assertTrue(MediaController.fastSyncEligible(true, java.util.Optional.of(new MediaController.FastSyncArtifact("fastsync/aw@org/fastsync.db", FastSyncTier.PER_USER))));
    }

    @Test
    public void aGroupMemberWithNoArtifactIsNotEligible() {
        // Review Focus 5. Must be a clean false so the client proceeds to a full sync.
        assertFalse(MediaController.fastSyncEligible(true, java.util.Optional.empty()));
    }

    @Test
    public void aSqliteUserIsNotOfferedTheRealmDump() {
        // #1059. Reachable via LoginActions.restoreDump falling through to restoreRealmDump.
        assertFalse(MediaController.realmDumpIsOfferable(true));
    }

    @Test
    public void aRealmUserIsStillOfferedTheRealmDump() {
        assertTrue(MediaController.realmDumpIsOfferable(false));
    }

    @Test
    public void aCatchmentlessUserNeverProbesTheNullCatchmentKey() {
        // "MobileDbBackupSqlite-null" is a real, writable key shared by every catchmentless user.
        assertEquals(java.util.Optional.empty(),
                resolve(aUser(), false, null, java.util.Set.of("MobileDbBackupSqlite-null")));
    }

    @Test
    public void aCatchmentlessUserStillFallsThroughToTheGeneratedSnapshot() {
        // Download degrades to the next tier rather than erroring, unlike upload.
        assertEquals(java.util.Optional.of("snapshots/aw@org/snapshot.db"),
                resolve(aUser(), false, null, java.util.Set.of("snapshots/aw@org/snapshot.db")));
    }

    @Test
    public void theSnapshotKeyIsSanitisedLikeTheFastSyncKey() {
        User user = new User();
        user.setUsername("a/../b");
        when(fastSyncKeyService.isPerUser(user)).thenReturn(false);

        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> MediaController.fastSyncDownloadKeyFor(user, "cat-uuid", fastSyncKeyService, key -> false));
    }
}
