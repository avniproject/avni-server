package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmNotReadyException;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmOrganisation;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmScheduler;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// The worker takes stored webhook events from the inbox and hands each to its handler as the event's organisation's
// system user. A handler that throws leaves the event for a later try; at the cap it fails and goes to Bugsnag.
public class EkaEventWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final long ORG = 1L;
    private static final AbdmOrganisation ORGANISATION = new AbdmOrganisation(ORG, "Org one", "system-1");

    private final AbdmInbox inbox = mock(AbdmInbox.class);
    private final AbdmAccess access = mock(AbdmAccess.class);
    private final AbdmHiuStore hiuStore = mock(AbdmHiuStore.class);
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmMessageLog messageLog = mock(AbdmMessageLog.class);
    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final EkaHipEvents hip = mock(EkaHipEvents.class);
    private final EkaHiuEvents hiu = mock(EkaHiuEvents.class);
    private final EkaHipDiscovery discovery = mock(EkaHipDiscovery.class);
    private ObjectProvider<EkaHiuEvents> hiuProvider;
    private final MovingClock clock = new MovingClock(NOW);
    private final List<AbdmOrganisation> ranAs = new ArrayList<>();
    private final List<Boolean> withoutLogin = new ArrayList<>();
    private EkaEventWorker worker;
    private long nextId = 1;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        when(access.recordSharingOn(ORG)).thenReturn(true);
        when(orgContext.organisation(ORG)).thenReturn(ORGANISATION);
        doAnswer(inv -> {
            ranAs.add(inv.getArgument(0));
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(orgContext).runAs(any(), any());
        when(orgContext.withoutLogin(any())).thenAnswer(inv -> {
            withoutLogin.add(true);
            return ((java.util.function.Supplier<?>) inv.getArgument(0)).get();
        });
        ObjectProvider<EkaHipEvents> hipProvider = mock(ObjectProvider.class);
        hiuProvider = mock(ObjectProvider.class);
        ObjectProvider<EkaHipDiscovery> discoveryProvider = mock(ObjectProvider.class);
        when(hipProvider.getIfAvailable()).thenReturn(hip);
        when(hiuProvider.getIfAvailable()).thenReturn(hiu);
        when(discoveryProvider.getIfAvailable()).thenReturn(discovery);
        worker = new EkaEventWorker(inbox, new EkaEventGate(access, hiuStore), orgContext, messageLog, mock(AbdmScheduler.class), bugsnag,
                hipProvider, hiuProvider, discoveryProvider, clock);
    }

    @Test
    public void aStoredLinkEventIsHandedToTheHipAsItsOrganisation_loggedOnce_andMarkedDone() {
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 1);

        assertEquals(1, worker.drain());

        assertEquals(List.of(ORGANISATION), ranAs);
        ArgumentCaptor<JsonNode> handed = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(messageLog, hip, inbox);
        order.verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.link_care_context"), any(), any());
        order.verify(hip).linkCareContext(handed.capture());
        order.verify(inbox).done(link.id());
        assertEquals("wh-link-1", handed.getValue().get("transaction_id").asText());
        verify(hip, never()).hipDataFetch(any(), anyBoolean());
        verifyNoInteractions(hiu, bugsnag);
    }

    @Test
    public void aRetriedEventIsNotLoggedAsTrafficAgain() {
        stored("link-care-context.json", "abha.link_care_context", 2);

        worker.drain();

        verify(hip).linkCareContext(any());
        verifyNoInteractions(messageLog);
    }

    @Test
    public void eachEventGoesToItsHandler() {
        stored("hip-data-fetch.json", "abha.hip_data_fetch", 1);
        stored("consent-update.json", "abha.consent_update", 1);
        stored("hiu-data-push-webhook.json", "abha.hiu_data_push", 1);

        worker.drain();

        verify(hip).hipDataFetch(any(), anyBoolean());
        verify(hiu).consentUpdate(any());
        verify(hiu).hiuDataPush(any(), anyBoolean());
        verify(hip, never()).linkCareContext(any());
    }

    @Test
    public void closeVariantsOfTheEventNamesAreRoutedToo() {
        stored("link-care-context.json", "LINK_CARE_CONTEXT", 1);
        stored("link-care-context.json", "abha.care_context_link", 1);
        stored("hip-data-fetch.json", "abdm.hip-data-fetch", 1);
        stored("hip-data-fetch.json", "abha.consentUpdate", 1);

        worker.drain();

        verify(hip, times(2)).linkCareContext(any());
        verify(hip).hipDataFetch(any(), anyBoolean());
        verify(hiu).consentUpdate(any());
    }

    @Test
    public void anEventWithoutAHandlerIsLoggedAndDone() {
        AbdmInbox.Event created = stored("link-care-context.json", "abha.created", 1);

        worker.drain();

        verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.created"), any(), any());
        verify(inbox).done(created.id());
        verifyNoInteractions(hip, hiu, discovery);
    }

    @Test
    public void theDiscoveryConfirmEventIsNotTakenForALinkStatus() {
        stored("link-care-context.json", "abha.context_discover_link_confirm", 1);

        worker.drain();

        verifyNoInteractions(hip, hiu);
        verify(discovery).linkConfirm(any());
    }

    // USER_INIT_LINK_601 to 607: the three discovery events reach patient-led linking (EKA_ABDM_SPEC.md 2.7).
    @Test
    public void eachDiscoveryEventGoesToPatientLedLinking() {
        stored("link-care-context.json", "abha.care_context_discover", 1);
        stored("link-care-context.json", "abha.care_context_discover_link_init", 1);

        worker.drain();

        verify(discovery).discover(any());
        verify(discovery).linkInit(any());
        verifyNoInteractions(hip, hiu);
    }

    @Test
    public void withoutAnHiuHandlerAConsentUpdateIsOnlyLogged() {
        when(hiuProvider.getIfAvailable()).thenReturn(null);
        AbdmInbox.Event update = stored("consent-update.json", "abha.consent_update", 1);

        worker.drain();

        verify(messageLog).incoming(eq(AbdmPaths.IN_EKA_WEBHOOK + "#abha.consent_update"), any(), any());
        verify(inbox).done(update.id());
        verifyNoInteractions(hiu);
    }

    // H4: Eka was told the event is ours, so a failed handling is ours to try again, not Eka's.
    @Test
    public void aHandlerThatThrowsLeavesTheEventForALaterTry_withoutAnAlarmYet() {
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 1);
        doThrow(new IllegalStateException("database down")).when(hip).linkCareContext(any());

        worker.drain();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(inbox).retry(eq(link), error.capture(), eq(NOW));
        assertTrue(error.getValue(), error.getValue().contains("database down"));
        verify(inbox, never()).done(anyLong());
        verifyNoInteractions(bugsnag);
    }

    @Test
    public void anEventThatReachesTheCapFailsAndGoesToBugsnag() {
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 10);
        IllegalStateException failure = new IllegalStateException("database down");
        doThrow(failure).when(hip).linkCareContext(any());
        when(inbox.retry(eq(link), anyString(), any())).thenReturn(true);

        worker.drain();

        verify(bugsnag).notify(failure);
    }

    // F1: a patient's withdrawal is never given up. Before the cap it is tried again quietly, as any event is.
    @Test
    public void aWithdrawalThatFailsBeforeTheCapIsTriedAgainQuietly_neverToBeGivenUp() {
        AbdmInbox.Event revoked = ending("REVOKED", 3);
        doThrow(new IllegalStateException("database down")).when(hiu).consentUpdate(any());

        worker.drain();

        verify(inbox).retryUntilHandled(eq(revoked), contains("database down"), eq(NOW));
        verify(inbox, never()).retry(any(), anyString(), any());
        verify(inbox, never()).done(anyLong());
        verifyNoInteractions(bugsnag);
    }

    // F1: from the cap on it keeps its body and is tried again slowly, and each try that fails alarms as not applied.
    @Test
    public void aWithdrawalPastTheCapIsTriedAgain_andAlarmsAsNotApplied() {
        for (String status : List.of("REVOKED", "DENIED", "EXPIRED")) {
            reset(inbox, bugsnag);
            waiting.clear();
            AbdmInbox.Event ended = ending(status, AbdmInbox.MAX_ATTEMPTS + 1);
            doThrow(new IllegalStateException("database down")).when(hiu).consentUpdate(any());

            worker.drain();

            verify(inbox).retryUntilHandled(eq(ended), contains("database down"), eq(NOW));
            verify(inbox, never()).retry(any(), anyString(), any());
            ArgumentCaptor<Throwable> alarm = ArgumentCaptor.forClass(Throwable.class);
            verify(bugsnag).notify(alarm.capture());
            assertTrue(alarm.getValue() instanceof EkaEventWorker.WithdrawalNotApplied);
            assertTrue(alarm.getValue().getMessage(), alarm.getValue().getMessage().startsWith("Withdrawal not applied"));
        }
    }

    // A grant is not a withdrawal: at the cap it is given up as any other event is.
    @Test
    public void aGrantThatReachesTheCapIsGivenUpAsAnyEventIs() {
        AbdmInbox.Event granted = stored("consent-update.json", "abha.consent_update", AbdmInbox.MAX_ATTEMPTS);
        doThrow(new IllegalStateException("database down")).when(hiu).consentUpdate(any());
        when(inbox.retry(eq(granted), anyString(), any())).thenReturn(true);

        worker.drain();

        verify(inbox).retry(eq(granted), anyString(), eq(NOW));
        verify(inbox, never()).retryUntilHandled(any(), anyString(), any());
    }

    // F1: an organisation whose system user cannot act still has its patient's withdrawal applied, with no login, and is
    // reported; the event is done.
    @Test
    public void withAnUnusableSystemUserAWithdrawalIsAppliedWithoutALogin_reported_andDone() {
        AbdmInbox.Event revoked = ending("REVOKED", 1);
        AbdmOrgContext.SystemUserException cannotAct = new AbdmOrgContext.SystemUserException(ORGANISATION, "its system user is voided", null);
        doThrow(cannotAct).when(orgContext).runAs(any(), any());
        when(hiu.endConsentWithoutLogin(eq(ORG), any())).thenReturn(true);

        worker.drain();

        verify(hiu).endConsentWithoutLogin(eq(ORG), any());
        verify(hiu, never()).consentUpdate(any());
        verify(inbox).done(revoked.id());
        verify(bugsnag).notify(cannotAct);
        assertEquals(List.of(true), withoutLogin);
    }

    // What is not held here yet (a request not mapped) waits for the next try, when the system user may act again.
    @Test
    public void withAnUnusableSystemUserAWithdrawalNotWhollyHeldHereWaitsForTheNextTry() {
        AbdmInbox.Event revoked = ending("REVOKED", 1);
        doThrow(new AbdmOrgContext.SystemUserException(ORGANISATION, "its system user is voided", null)).when(orgContext).runAs(any(), any());
        when(hiu.endConsentWithoutLogin(eq(ORG), any())).thenReturn(false);

        worker.drain();

        verify(hiu).endConsentWithoutLogin(eq(ORG), any());
        verify(inbox).retryUntilHandled(eq(revoked), contains("system user"), eq(NOW));
        verify(inbox, never()).done(anyLong());
    }

    @Test
    public void withAnUnusableSystemUserAnyOtherEventWaitsAsBefore_withNothingDoneWithoutALogin() {
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 1);
        doThrow(new AbdmOrgContext.SystemUserException(ORGANISATION, "its system user is voided", null)).when(orgContext).runAs(any(), any());

        worker.drain();

        verify(inbox).retry(eq(link), contains("system user"), eq(NOW));
        verify(hiu, never()).endConsentWithoutLogin(anyLong(), any());
        assertTrue(withoutLogin.isEmpty());
    }

    // F6: a share's handler is told whether this is the inbox's last try, so a failure that may pass is thrown before it
    // and recorded as final on it.
    @Test
    public void aShareIsToldWhetherItIsTheLastTry() {
        stored("hip-data-fetch.json", "abha.hip_data_fetch", 1);
        stored("hiu-data-push-webhook.json", "abha.hiu_data_push", AbdmInbox.MAX_ATTEMPTS - 1);
        stored("hip-data-fetch.json", "abha.hip_data_fetch", AbdmInbox.MAX_ATTEMPTS);
        stored("hiu-data-push-webhook.json", "abha.hiu_data_push", AbdmInbox.MAX_ATTEMPTS);

        worker.drain();

        verify(hip).hipDataFetch(any(), eq(false));
        verify(hip).hipDataFetch(any(), eq(true));
        verify(hiu).hiuDataPush(any(), eq(false));
        verify(hiu).hiuDataPush(any(), eq(true));
    }

    @Test
    public void aDataPushWaitingForItsGrantIsTriedAgain() {
        AbdmInbox.Event push = stored("hiu-data-push-webhook.json", "abha.hiu_data_push", 1);
        doThrow(new AbdmNotReadyException("no grant yet")).when(hiu).hiuDataPush(any(), anyBoolean());

        worker.drain();

        verify(inbox).retry(eq(push), anyString(), eq(NOW));
        verify(inbox, never()).done(anyLong());
        verifyNoInteractions(bugsnag);
    }

    @Test
    public void anOrganisationWithoutASystemUserLeavesTheEventForALaterTry() {
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 1);
        doThrow(new RuntimeException("ABDM work for organisation 1 not run: no system user")).when(orgContext).runAs(any(), any());

        worker.drain();

        verify(inbox).retry(eq(link), anyString(), eq(NOW));
        verifyNoInteractions(hip);
    }

    // Every ABDM entry point checks the organisation's switch first: one switched off since is not handled.
    @Test
    public void anEventOfAnOrganisationWhoseRecordSharingIsNowOffIsNotHandled() {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        AbdmInbox.Event link = stored("link-care-context.json", "abha.link_care_context", 1);

        worker.drain();

        verify(inbox).fail(eq(link.id()), anyString(), eq(NOW));
        verifyNoInteractions(hip, orgContext);
    }

    // A withdrawal queued or arriving after record sharing went off is still applied, so its records are erased.
    @Test
    public void withRecordSharingOffAWithdrawalIsStillHandledAsTheOrganisation() {
        when(access.recordSharingOn(ORG)).thenReturn(false);
        when(hiuStore.holdsConsentsOrRecords(ORG)).thenReturn(true);
        AbdmInbox.Event revoked = new AbdmInbox.Event(nextId++, ORG, "eka", "revoked-1", "abha.consent_update", "CONSENT_UPDATE",
                fixture("consent-update.json").replace("\"GRANTED\"", "\"REVOKED\""), 1);
        AbdmInbox.Event push = stored("hiu-data-push-webhook.json", "abha.hiu_data_push", 1);
        when(inbox.claim(anyInt(), any())).thenReturn(List.of(revoked, push), List.of());

        worker.drain();

        verify(hiu).consentUpdate(any());
        verify(inbox).done(revoked.id());
        verify(hiu, never()).hiuDataPush(any(), anyBoolean());
        verify(inbox).fail(eq(push.id()), anyString(), eq(NOW));
        assertEquals(List.of(ORGANISATION), ranAs);
    }

    // Fix round 2: an interrupted thread (the scheduler stopping on a deploy) dispatches nothing more: each Eka call would
    // fail at once, and a share might be taken as done. What it holds is swept back for another try.
    @Test
    public void anInterruptedDrainDispatchesNoFurtherEvent_andClaimsNoMore() {
        stored("hip-data-fetch.json", "abha.hip_data_fetch", 1);
        stored("hip-data-fetch.json", "abha.hip_data_fetch", 1);
        doAnswer(inv -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(hip).hipDataFetch(any(), anyBoolean());

        try {
            assertEquals(1, worker.drain());
        } finally {
            Thread.interrupted();
        }

        verify(hip, times(1)).hipDataFetch(any(), anyBoolean());
        verify(inbox, times(1)).claim(anyInt(), any());
    }

    @Test
    public void theWorkerTakesNoNewEventsAfterItsDeadline() {
        when(inbox.claim(anyInt(), any())).thenAnswer(inv -> {
            clock.advance(Duration.ofMinutes(1));
            return List.of(event(nextId++, "link-care-context.json", "abha.link_care_context", 1));
        });

        int handled = worker.drain();

        assertEquals(EkaEventWorker.DRAIN_BUDGET.toMinutes(), handled);
    }

    @Test
    public void eventsGivenUpOnBySweepingGoToBugsnag() {
        when(inbox.sweep(NOW)).thenReturn(new AbdmInbox.Sweep(3, 1));

        worker.sweep();

        verify(bugsnag).notify(any(IllegalStateException.class));
    }

    @Test
    public void eventsSweptBackForATryRaiseNoAlarm() {
        when(inbox.sweep(NOW)).thenReturn(new AbdmInbox.Sweep(2, 0));

        worker.sweep();

        verifyNoInteractions(bugsnag);
    }

    private final List<AbdmInbox.Event> waiting = new ArrayList<>();

    // A consent update that ends the consent with this status, on this try.
    private AbdmInbox.Event ending(String status, int attempts) {
        AbdmInbox.Event event = new AbdmInbox.Event(nextId++, ORG, "eka", "ending-" + nextId, "abha.consent_update", "CONSENT_UPDATE",
                fixture("consent-update.json").replace("\"GRANTED\"", "\"" + status + "\""), attempts);
        waiting.add(event);
        when(inbox.claim(anyInt(), any())).thenReturn(new ArrayList<>(waiting), List.of());
        return event;
    }

    private AbdmInbox.Event stored(String fixture, String eventName, int attempts) {
        AbdmInbox.Event event = event(nextId++, fixture, eventName, attempts);
        waiting.add(event);
        when(inbox.claim(anyInt(), any())).thenReturn(new ArrayList<>(waiting), List.of());
        return event;
    }

    private static AbdmInbox.Event event(long id, String fixture, String eventName, int attempts) {
        ObjectNode body = (ObjectNode) AbdmJson.read(fixture(fixture));
        body.put("event", eventName);
        return new AbdmInbox.Event(id, ORG, "eka", "event-" + id, eventName, EkaEventRoute.of(eventName).name(), AbdmJson.write(body), attempts);
    }

    private static String fixture(String name) {
        try (InputStream in = EkaEventWorkerTest.class.getResourceAsStream("/abdm/eka/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        void set(Instant to) {
            now = to;
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
