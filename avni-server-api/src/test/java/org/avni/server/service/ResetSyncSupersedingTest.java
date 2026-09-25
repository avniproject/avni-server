package org.avni.server.service;

import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.ResetSyncRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.ResetSync;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ResetSyncSupersedingTest {
    private static final DateTime CATCHMENT_CHANGED_AT = DateTime.parse("2026-03-01T10:00:00Z");
    private static final Date DUMP_AFTER_THE_CHANGE = DateTime.parse("2026-03-01T11:00:00Z").toDate();
    private static final Date DUMP_BEFORE_THE_CHANGE = DateTime.parse("2026-03-01T09:00:00Z").toDate();

    @Mock
    private ResetSyncRepository resetSyncRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private LocationRepository locationRepository;

    private ResetSyncService service;
    private User user;

    @Before
    public void setUp() {
        initMocks(this);
        service = new ResetSyncService(resetSyncRepository, userRepository, individualRepository,
                subjectTypeRepository, locationRepository);
        user = new User();
        user.setUsername("worker-b@org");
        user.setCatchment(aCatchmentLastChangedAt(CATCHMENT_CHANGED_AT));
    }

    private Catchment aCatchmentLastChangedAt(DateTime lastModified) {
        Catchment catchment = new Catchment();
        catchment.setUuid("cat-uuid");
        catchment.setLastModifiedDateTime(lastModified);
        return catchment;
    }

    private ResetSync aUserScopedReset(String uuid) {
        ResetSync resetSync = new ResetSync();
        resetSync.setUuid(uuid);
        resetSync.setUser(user);
        return resetSync;
    }

    private ResetSync aSubjectTypeScopedReset(String uuid) {
        ResetSync resetSync = aUserScopedReset(uuid);
        resetSync.setSubjectType(new SubjectType());
        return resetSync;
    }

    private void theUserHas(ResetSync... resetSyncs) {
        when(resetSyncRepository.findAllByUserAndIsVoidedFalse(user)).thenReturn(Arrays.asList(resetSyncs));
    }

    @Test
    public void aDumpTakenAfterTheCatchmentChangedSupersedesTheResetItRaised() {
        // Worker B, moved into catchment C. The dump was uploaded by worker A after the catchment
        // was last touched, so it already holds exactly the rows B's reset would re-download.
        theUserHas(aUserScopedReset("reset-b"));

        assertEquals(Collections.singletonList("reset-b"),
                service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void aDumpTakenBeforeTheCatchmentChangedSupersedesNothing() {
        // The catchment's locations were edited after the dump was made, so the dump genuinely
        // predates the change and the reset must still fire.
        theUserHas(aUserScopedReset("reset-b"));

        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(user, DUMP_BEFORE_THE_CHANGE));
    }

    @Test
    public void aDumpTakenAtTheInstantOfTheChangeSupersedesNothing() {
        // Strictly newer. At equal timestamps there is no telling which happened first, and wrongly
        // superseding leaves the device holding stale data.
        theUserHas(aUserScopedReset("reset-b"));

        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(user, CATCHMENT_CHANGED_AT.toDate()));
    }

    @Test
    public void theComparisonIsAgainstTheCatchmentNotAgainstTheReset() {
        // Comparing against the reset's own timestamp would reject B's perfectly good dump, because
        // B's reset was raised after A uploaded it. The reset's age is deliberately never consulted.
        assertTrue(ResetSyncService.supersedesCatchmentResets(DUMP_AFTER_THE_CHANGE, CATCHMENT_CHANGED_AT));
        assertFalse(ResetSyncService.supersedesCatchmentResets(DUMP_BEFORE_THE_CHANGE, CATCHMENT_CHANGED_AT));
    }

    @Test
    public void aSubjectTypeScopedResetIsNeverSuperseded() {
        // recordSyncAttributeChange raises these: a sync attribute definition moved, and no
        // catchment dump can satisfy that.
        theUserHas(aSubjectTypeScopedReset("reset-sync-attribute"));

        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void onlyTheUserScopedResetsComeBackOutOfAMixedSet() {
        theUserHas(aUserScopedReset("reset-catchment-1"),
                aSubjectTypeScopedReset("reset-sync-attribute"),
                aUserScopedReset("reset-catchment-2"));

        assertEquals(Arrays.asList("reset-catchment-1", "reset-catchment-2"),
                service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void everyQualifyingResetComesBackBecauseTheServerCannotTellWhichTheDeviceApplied() {
        // The server does not track client-side migration state, and the client's marking is
        // idempotent, so the answer is all of them rather than a guess at the unapplied ones.
        theUserHas(aUserScopedReset("reset-1"), aUserScopedReset("reset-2"), aUserScopedReset("reset-3"));

        assertEquals(Arrays.asList("reset-1", "reset-2", "reset-3"),
                service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void aUserWithNoResetsGetsAnEmptyList() {
        theUserHas();

        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void aUserWithNoCatchmentGetsAnEmptyListWithoutQueryingResets() {
        User catchmentless = new User();
        catchmentless.setUsername("no-catchment@org");

        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(catchmentless, DUMP_AFTER_THE_CHANGE));
        verify(resetSyncRepository, never()).findAllByUserAndIsVoidedFalse(any(User.class));
    }

    @Test
    public void noArtifactMeansNoTimestampAndSoAnEmptyList() {
        theUserHas(aUserScopedReset("reset-b"));

        assertEquals(Collections.emptyList(), service.getSupersededResetSyncUuids(user, null));
    }

    @Test
    public void aNullUserGetsAnEmptyList() {
        assertEquals(Collections.emptyList(),
                service.getSupersededResetSyncUuids(null, DUMP_AFTER_THE_CHANGE));
    }

    @Test
    public void aFailureWhileWorkingItOutLeavesEveryResetInForceRatherThanBreakingTheDownload() {
        when(resetSyncRepository.findAllByUserAndIsVoidedFalse(user))
                .thenThrow(new RuntimeException("connection reset"));

        List<String> uuids = service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE);

        assertEquals(Collections.emptyList(), uuids);
    }

    @Test
    public void nothingIsVoidedOrWrittenBackWhileWorkingItOut() {
        // Read-only: the client decides what to mark, the server only reports.
        theUserHas(aUserScopedReset("reset-b"));

        service.getSupersededResetSyncUuids(user, DUMP_AFTER_THE_CHANGE);

        verify(resetSyncRepository, never()).save(any(ResetSync.class));
        verify(resetSyncRepository, never()).saveAll(any());
    }
}
