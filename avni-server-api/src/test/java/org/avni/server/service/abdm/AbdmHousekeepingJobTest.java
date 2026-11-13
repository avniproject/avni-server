package org.avni.server.service.abdm;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmTestSupport;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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
    private final AbdmHiuStore hiuStore = mock(AbdmHiuStore.class);
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
        doAnswer(inv -> {
            ((Consumer<Instant>) inv.getArgument(3)).accept(deadline);
            return null;
        }).when(runner).forOrganisations(anyString(), any(), any(), any());
        doAnswer(inv -> {
            ((Consumer<Instant>) inv.getArgument(3)).accept(deadline);
            return null;
        }).when(runner).forOrganisations(anyString(), any(), any(), any(), any());
    }

    private AbdmHousekeepingJob job(AbdmSettings settings) {
        return job(settings, 30);
    }

    private AbdmHousekeepingJob job(AbdmSettings settings, int messageRetentionDays) {
        return new AbdmHousekeepingJob(runner, mock(AbdmScheduler.class), settings, messageLog, inbox, hiuData, hiuStore, hipStore, ekaHip,
                ekaHiu, clock, new AbdmConfig(null, messageRetentionDays));
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
        when(ekaHip.linkQueued(eq("subject-2"), anyBoolean())).thenThrow(AbdmRequestException.conflict("This subject's verified ABHA has neither an address nor a number"));

        job(twoHips).linkQueuedVisits();

        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.VISIT_LINKING), any(), any());
        verify(ekaHip).linkQueued(eq("subject-1"), anyBoolean());
        verify(ekaHip).linkQueued(eq("subject-2"), anyBoolean());
        verify(ekaHip).linkQueued(eq("subject-3"), anyBoolean());
        verify(hipStore).failQueuedForGood("subject-2", "hip-1", "This subject's verified ABHA has neither an address nor a number");
        verify(hipStore).failQueuedForGood("subject-2", "hip-2", "This subject's verified ABHA has neither an address nor a number");
    }

    // HIP_INTI_LINK_505: visits queued on save are linked without anyone pressing a button. A patient Avni cannot link
    // (removed, a verified ABHA with neither address nor number) is given up: no later try changes that.
    @Test
    public void queuedVisitsAreLinkedPatientByPatient_andOneAvniCannotLinkIsGivenUp() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenThrow(AbdmRequestException.notFound("Subject not found"));

        job().linkQueuedVisits();

        verify(ekaHip).linkQueued(eq("subject-1"), anyBoolean());
        verify(ekaHip).linkQueued(eq("subject-2"), anyBoolean());
        verify(hipStore).failQueuedForGood("subject-1", AbdmTestSupport.HIP_ID, "Subject not found");
        verify(hipStore, never()).failQueued(any(), any(), any());
    }

    // A patient whose ABHA Eka has not verified is neither given up nor tried again: they wait for a verified ABHA. The
    // write moves only rows unchanged since the job looked, so a verified ABHA saved meanwhile is not undone.
    @Test
    public void aPatientWithoutAVerifiedAbhaWaitsForOne() {
        Instant looked = Instant.parse("2026-11-12T09:59:00Z");
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1"));
        when(hipStore.databaseNow()).thenReturn(looked);
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenThrow(new EkaHipService.NoVerifiedAbha());

        job().linkQueuedVisits();

        org.mockito.InOrder order = inOrder(hipStore, ekaHip);
        order.verify(hipStore).databaseNow();
        order.verify(ekaHip).linkQueued(eq("subject-1"), anyBoolean());
        order.verify(hipStore).awaitVerifiedAbha("subject-1", AbdmTestSupport.HIP_ID, EkaHipService.NO_EKA_ID, looked);
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
        verify(hipStore, never()).failQueued(any(), any(), any());
    }

    // A patient whose Eka ID another live patient claimed first waits too, and the visits say so.
    @Test
    public void aPatientWhoseEkaIdIsHeldByAnotherWaitsSayingSo() {
        Instant looked = Instant.parse("2026-11-12T09:59:00Z");
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1"));
        when(hipStore.databaseNow()).thenReturn(looked);
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean()))
                .thenThrow(new EkaHipService.NoVerifiedAbha(org.avni.server.service.abha.AbhaVerifiedRecords.HELD_BY_ANOTHER));

        job().linkQueuedVisits();

        verify(hipStore).awaitVerifiedAbha("subject-1", AbdmTestSupport.HIP_ID, "Eka ID held by another live patient", looked);
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
    }

    // Anything else may pass: counted, and tried again after a growing wait (AbdmHipStore.failQueued).
    @Test
    public void aPatientWhoseLinkFailedOtherwiseIsTriedAgainLater() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenThrow(new IllegalStateException("database unavailable"));

        job().linkQueuedVisits();

        verify(hipStore).failQueued("subject-1", AbdmTestSupport.HIP_ID, "database unavailable");
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
    }

    // F5: Eka failing for everyone (login, 401, 403, a server error, no answer) is no patient's fault: nothing is counted
    // against any visit, no further patient is tried, and the failure goes to the runner, which records the organisation's
    // run as failed and reports it (AbdmJobRunner).
    @Test
    public void anEkaWideFailureStopsTheRun_countsNothing_andIsLeftForTheRunnerToReport() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2"));
        org.avni.server.service.abdm.eka.EkaCallException lapsedKey = new org.avni.server.service.abdm.eka.EkaCallException(0, null,
                "Eka login failed: Eka login failed with HTTP 401");
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenThrow(lapsedKey);

        assertSame(lapsedKey, org.junit.Assert.assertThrows(org.avni.server.service.abdm.eka.EkaCallException.class, () -> job().linkQueuedVisits()));

        verify(ekaHip, never()).linkQueued(eq("subject-2"), anyBoolean());
        verify(hipStore, never()).failQueued(any(), any(), any());
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
        verify(hipStore, never()).awaitVerifiedAbha(any(), any(), any(), any());
    }

    // R21: until Eka has taken a link in this run, a failure of Eka's own stops the run uncounted (above); once it has, the
    // next patients' links go with that said, so a failure Eka gives only them is theirs, counted (EkaHipService.linkQueued),
    // and the run goes on. A patient Eka always fails is then given up at LINK_ATTEMPTS instead of stopping every run.
    @Test
    public void onceEkaHasTakenALinkInTheRunTheNextPatientsFailuresAreTheirs_andTheRunGoesOn() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2", "subject-3", "subject-4"));
        when(ekaHip.linkQueued("subject-1", false)).thenReturn(false);
        when(ekaHip.linkQueued("subject-2", false)).thenReturn(true);
        when(ekaHip.linkQueued("subject-3", true)).thenReturn(false);
        when(ekaHip.linkQueued("subject-4", true)).thenReturn(false);

        job().linkQueuedVisits();

        org.mockito.InOrder order = inOrder(ekaHip);
        order.verify(ekaHip).linkQueued("subject-1", false);
        order.verify(ekaHip).linkQueued("subject-2", false);
        order.verify(ekaHip).linkQueued("subject-3", true);
        order.verify(ekaHip).linkQueued("subject-4", true);
    }

    // R21: the stop holds only while no patient in the run has linked; a later run starts over, uncounted.
    @Test
    public void inANewRunNoPatientHasLinkedYet() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenReturn(true);

        job().linkQueuedVisits();
        job().linkQueuedVisits();

        verify(ekaHip, times(2)).linkQueued("subject-1", false);
    }

    // Carried from R2-2's review: the scheduler stopping is seen even if the pool cleared the thread's interrupt flag.
    @Test
    public void aRunStopsWhenTheSchedulerIsStopping_evenWithoutAnInterrupt() {
        AbdmScheduler stopping = mock(AbdmScheduler.class);
        when(stopping.toldToStop()).thenReturn(true);
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1"));

        new AbdmHousekeepingJob(runner, stopping, AbdmTestSupport.settings(), messageLog, inbox, hiuData, hiuStore, hipStore, ekaHip,
                ekaHiu, clock, new AbdmConfig(null, 30)).linkQueuedVisits();

        verify(ekaHip, never()).linkQueued(any(), anyBoolean());
    }

    @Test
    public void aFailureWhileTheSchedulerIsStoppingCountsAgainstNoVisit() {
        AbdmScheduler scheduler = mock(AbdmScheduler.class);
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenAnswer(inv -> {
            when(scheduler.toldToStop()).thenReturn(true);
            throw new IllegalStateException("could not get a connection");
        });

        new AbdmHousekeepingJob(runner, scheduler, AbdmTestSupport.settings(), messageLog, inbox, hiuData, hiuStore, hipStore, ekaHip,
                ekaHiu, clock, new AbdmConfig(null, 30)).linkQueuedVisits();

        verify(ekaHip, never()).linkQueued(eq("subject-2"), anyBoolean());
        verify(hipStore, never()).failQueued(any(), any(), any());
    }

    // Carried from R2-1: a run told to stop (the server shutting down) takes no further patient, and a failure on the way
    // out counts against no visit.
    @Test
    public void aRunToldToStopTakesNoFurtherPatient_andCountsNothing() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenAnswer(inv -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("could not get a connection: interrupted");
        });
        try {
            job().linkQueuedVisits();
        } finally {
            org.junit.Assert.assertTrue(Thread.interrupted());
        }

        verify(ekaHip, never()).linkQueued(eq("subject-2"), anyBoolean());
        verify(hipStore, never()).failQueued(any(), any(), any());
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
    }

    // Links Eka never answered are queued again, and a removed patient's visits given up, before the run picks patients.
    @Test
    public void theQueueIsSettledUnderEachHipIdBeforePatientsAreTaken() {
        AbdmSettings twoHips = AbdmTestSupport.settings(AbdmTestSupport.ekaSettings(
                new JsonObject().with(EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2"))));

        job(twoHips).linkQueuedVisits();

        org.mockito.InOrder order = inOrder(hipStore);
        order.verify(hipStore).settleLinkQueue("hip-1");
        order.verify(hipStore).subjectsToLink("hip-1", 50);
        order.verify(hipStore).settleLinkQueue("hip-2");
        order.verify(hipStore).subjectsToLink("hip-2", 50);
    }

    @Test
    public void linkingTakesNoNewPatientAfterTheDeadline_andLeavesTheRestForTheNextRun() {
        when(hipStore.subjectsToLink(AbdmTestSupport.HIP_ID, 50)).thenReturn(List.of("subject-1", "subject-2", "subject-3"));
        when(ekaHip.linkQueued(eq("subject-1"), anyBoolean())).thenAnswer(inv -> {
            clock.advance(Duration.ofMinutes(5));
            return true;
        });

        job().linkQueuedVisits();

        verify(ekaHip).linkQueued(eq("subject-1"), anyBoolean());
        verify(ekaHip, never()).linkQueued(eq("subject-2"), anyBoolean());
        verify(ekaHip, never()).linkQueued(eq("subject-3"), anyBoolean());
        verify(hipStore, never()).failQueued(any(), any(), any());
        verify(hipStore, never()).failQueuedForGood(any(), any(), any());
    }

    // Kept traffic and finished events can carry a patient's details, so the purge visits every organisation holding old
    // ones, record sharing on or not.
    @Test
    @SuppressWarnings("unchecked")
    public void oldTrafficAndFinishedWebhookEventsAreDeletedInEveryOrganisationHoldingThem() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        when(messageLog.organisationsWithTrafficBefore(cutoff)).thenReturn(List.of(3L, 5L));
        when(inbox.organisationsWithFinishedEventsBefore(cutoff)).thenReturn(List.of(5L, 4L));

        job().purgeOldTraffic();

        ArgumentCaptor<Supplier<List<Long>>> holding = ArgumentCaptor.forClass(Supplier.class);
        ArgumentCaptor<LongConsumer> withoutLogin = ArgumentCaptor.forClass(LongConsumer.class);
        verify(runner).forOrganisations(eq(AbdmHousekeepingJob.TRAFFIC_PURGE), any(), holding.capture(), any(), withoutLogin.capture());
        assertEquals(List.of(3L, 4L, 5L), holding.getValue().get());
        verify(runner, never()).forEachOrganisation(eq(AbdmHousekeepingJob.TRAFFIC_PURGE), any(), any());
        verify(messageLog).purgeOlderThan(Duration.ofDays(30));
        verify(inbox).purgeFinishedOlderThan(cutoff);

        // F1: an organisation whose system user cannot act is purged with no login, each statement naming it.
        withoutLogin.getValue().accept(4L);
        verify(messageLog).purgeOlderThan(4L, Duration.ofDays(30));
        verify(inbox).purgeFinishedOlderThan(4L, cutoff);
    }

    // Fix round 1: the purge keeps to the deployment's retention as the status page counts over it (AbdmConfig), so the
    // two cannot drift.
    @Test
    public void thePurgeKeepsToTheDeploymentsRetention() {
        Instant cutoff = NOW.minus(Duration.ofDays(45));

        job(AbdmTestSupport.settings(), 45).purgeOldTraffic();

        verify(messageLog).purgeOlderThan(Duration.ofDays(45));
        verify(inbox).purgeFinishedOlderThan(cutoff);
    }

    // Consents expire and their records are erased in every organisation holding them, record sharing on or not;
    // the retry and the settling of requests Eka gave no clear answer to, which ask Eka, only where record sharing is on.
    @Test
    @SuppressWarnings("unchecked")
    public void endedConsentsAreExpiredAndTheirRecordsErasedWhereverTheyAreHeld_andParkedUpdatesRetriedWhereSharingIsOn() {
        when(hiuStore.organisationsHoldingConsentsOrRecords()).thenReturn(List.of(7L));

        job().expireConsentsAndEraseRecords();

        ArgumentCaptor<Supplier<List<Long>>> holding = ArgumentCaptor.forClass(Supplier.class);
        ArgumentCaptor<LongConsumer> withoutLogin = ArgumentCaptor.forClass(LongConsumer.class);
        verify(runner).forOrganisations(eq(AbdmHousekeepingJob.CONSENT_EXPIRY), any(), holding.capture(), any(), withoutLogin.capture());
        assertEquals(List.of(7L), holding.getValue().get());
        // F1: an organisation whose system user cannot act has its consents expired and records erased with no login.
        withoutLogin.getValue().accept(7L);
        verify(hiuData).expireAndErase(7L);
        verify(runner, never()).forEachOrganisation(eq(AbdmHousekeepingJob.CONSENT_EXPIRY), any(), any());
        verify(runner).forEachOrganisation(eq(AbdmHousekeepingJob.CONSENT_UPDATE_RETRY), any(), any());
        verify(hiuData).expireAndErase();
        verify(ekaHiu).retryParkedUpdates();
        verify(ekaHiu).settleUnknownRequests();
        verify(runner, never()).forOrganisations(eq(AbdmHousekeepingJob.CONSENT_UPDATE_RETRY), any(), any(), any());
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
