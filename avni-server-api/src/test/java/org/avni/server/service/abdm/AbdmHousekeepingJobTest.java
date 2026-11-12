package org.avni.server.service.abdm;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// ABDM's scheduled upkeep, for each organisation the runner hands it (AbdmJobRunner, tested on its own).
public class AbdmHousekeepingJobTest {
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private final AbdmJobRunner runner = mock(AbdmJobRunner.class);
    private final AbdmMessageLog messageLog = mock(AbdmMessageLog.class);
    private final AbdmInbox inbox = mock(AbdmInbox.class);
    private final AbdmHiuDataService hiuData = mock(AbdmHiuDataService.class);
    private final AbdmHipStore hipStore = mock(AbdmHipStore.class);
    private final EkaHipService ekaHip = mock(EkaHipService.class);
    private final EkaHiuService ekaHiu = mock(EkaHiuService.class);
    private final MovingClock clock = new MovingClock(NOW);
    private Instant deadline;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        deadline = NOW.plus(Duration.ofMinutes(4));
        doAnswer(inv -> {
            ((Consumer<Instant>) inv.getArgument(2)).accept(deadline);
            return null;
        }).when(runner).forEachOrganisation(anyString(), any(), any());
    }

    private AbdmHousekeepingJob job(AbdmSettings settings) {
        return new AbdmHousekeepingJob(runner, mock(AbdmScheduler.class), settings, messageLog, inbox, hiuData, hipStore, ekaHip, ekaHiu,
                clock, 30);
    }

    private AbdmHousekeepingJob job() {
        return job(AbdmTestSupport.settings());
    }

    @Test
    public void queuedVisitsUnderEveryHipIdOfTheOrganisationAreLinked_eachPatientOnce() {
        AbdmSettings twoHips = AbdmTestSupport.settings(AbdmTestSupport.ekaSettings(
                new JsonObject().with(EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2"))));
        when(hipStore.subjectsToLink("hip-1", 50)).thenReturn(List.of("subject-1", "subject-2"));
        when(hipStore.subjectsToLink("hip-2", 50)).thenReturn(List.of("subject-2", "subject-3"));
        when(ekaHip.linkCareContexts("subject-2", true)).thenThrow(AbdmRequestException.conflict("Patient has no Eka ID"));

        job(twoHips).linkQueuedVisits();

        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.VISIT_LINKING), any(), any());
        verify(ekaHip).linkCareContexts("subject-1", true);
        verify(ekaHip).linkCareContexts("subject-2", true);
        verify(ekaHip).linkCareContexts("subject-3", true);
        verify(hipStore).failQueued("subject-2", "hip-1", "Patient has no Eka ID");
        verify(hipStore).failQueued("subject-2", "hip-2", "Patient has no Eka ID");
    }

    // HIP_INTI_LINK_505: visits queued on save are linked without anyone pressing a button.
    @Test
    public void queuedVisitsAreLinkedPatientByPatient_andOneThatFailsIsMarkedSoItWaitsBeforeTheNextTry() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2"));
        when(ekaHip.linkCareContexts("subject-1", true)).thenThrow(AbdmRequestException.conflict("Patient has no Eka ID"));

        job().linkQueuedVisits();

        verify(ekaHip).linkCareContexts("subject-1", true);
        verify(ekaHip).linkCareContexts("subject-2", true);
        verify(hipStore).failQueued("subject-1", AbdmTestSupport.HIP_ID, "Patient has no Eka ID");
    }

    @Test
    public void linkingTakesNoNewPatientAfterTheDeadline_andLeavesTheRestForTheNextRun() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2", "subject-3"));
        when(ekaHip.linkCareContexts("subject-1", true)).thenAnswer(inv -> {
            clock.advance(Duration.ofMinutes(5));
            return null;
        });

        job().linkQueuedVisits();

        verify(ekaHip).linkCareContexts("subject-1", true);
        verify(ekaHip, never()).linkCareContexts(eq("subject-2"), anyBoolean());
        verify(ekaHip, never()).linkCareContexts(eq("subject-3"), anyBoolean());
        verify(hipStore, never()).failQueued(any(), any(), any());
    }

    @Test
    public void oldTrafficAndHandledWebhookEventsAreDeletedForTheOrganisation() {
        job().purgeOldTraffic();

        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.TRAFFIC_PURGE), any(), any());
        verify(messageLog).purgeOlderThan(Duration.ofDays(30));
        verify(inbox).purgeFinishedOlderThan(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    public void endedConsentsAreExpiredAndTheirRecordsErased_andParkedConsentUpdatesRetried() {
        job().expireConsentsAndEraseRecords();

        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.CONSENT_EXPIRY), any(), any());
        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.CONSENT_UPDATE_RETRY), any(), any());
        verify(hiuData).expireAndErase();
        verify(ekaHiu).retryParkedUpdates();
    }

    // A clock the test moves forward.
    static class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
