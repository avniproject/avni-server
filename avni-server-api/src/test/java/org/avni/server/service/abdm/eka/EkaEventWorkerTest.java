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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmMessageLog messageLog = mock(AbdmMessageLog.class);
    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final EkaHipEvents hip = mock(EkaHipEvents.class);
    private final EkaHiuEvents hiu = mock(EkaHiuEvents.class);
    private final EkaHipDiscovery discovery = mock(EkaHipDiscovery.class);
    private ObjectProvider<EkaHiuEvents> hiuProvider;
    private final MovingClock clock = new MovingClock(NOW);
    private final List<AbdmOrganisation> ranAs = new ArrayList<>();
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
        ObjectProvider<EkaHipEvents> hipProvider = mock(ObjectProvider.class);
        hiuProvider = mock(ObjectProvider.class);
        ObjectProvider<EkaHipDiscovery> discoveryProvider = mock(ObjectProvider.class);
        when(hipProvider.getIfAvailable()).thenReturn(hip);
        when(hiuProvider.getIfAvailable()).thenReturn(hiu);
        when(discoveryProvider.getIfAvailable()).thenReturn(discovery);
        worker = new EkaEventWorker(inbox, access, orgContext, messageLog, mock(AbdmScheduler.class), bugsnag, hipProvider, hiuProvider,
                discoveryProvider, clock);
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
        verify(hip, never()).hipDataFetch(any());
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

        verify(hip).hipDataFetch(any());
        verify(hiu).consentUpdate(any());
        verify(hiu).hiuDataPush(any());
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
        verify(hip).hipDataFetch(any());
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

    @Test
    public void aDataPushWaitingForItsGrantIsTriedAgain() {
        AbdmInbox.Event push = stored("hiu-data-push-webhook.json", "abha.hiu_data_push", 1);
        doThrow(new AbdmNotReadyException("no grant yet")).when(hiu).hiuDataPush(any());

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
