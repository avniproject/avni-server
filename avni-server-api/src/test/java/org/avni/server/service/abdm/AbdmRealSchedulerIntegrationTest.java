package org.avni.server.service.abdm;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.service.abdm.eka.EkaEventGate;
import org.avni.server.service.abdm.eka.EkaEventWorker;
import org.avni.server.service.abdm.eka.EkaHipEvents;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// F12: ABDM's jobs and webhook worker started on a real AbdmScheduler, as they start when the server is ready. The shared
// test context runs nothing on ABDM's threads (AbdmTestScheduling), so without this test the code that starts them could
// go unnoticed. Each test has its own scheduler, firing every second, stopped before its rows are cleared.
public class AbdmRealSchedulerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long ORG = 1L;
    private static final String EVERY_SECOND = "* * * * * *";
    private static final long WAIT_MS = 15_000;

    @Autowired
    private AbdmInbox inbox;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    private JdbcTemplate jdbc;
    private AbdmScheduler scheduler;

    @Before
    public void startAScheduler() {
        jdbc = (JdbcTemplate) namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_inbound_event");
        scheduler = new AbdmScheduler();
    }

    @After
    public void stopTheSchedulerThenClear() {
        scheduler.shutdown();
        jdbc.update("delete from abdm_inbound_event");
    }

    // EkaEventWorker.start: the drain hands a stored event to its handler and marks it done; the sweep gives up an event
    // left in progress on its last try, and puts back one left earlier, which the drain then handles.
    @Test
    public void theWorkerStartedOnTheSchedulerDrainsStoredEventsAndSweepsStuckOnes() throws Exception {
        AbdmAccess access = mock(AbdmAccess.class);
        when(access.recordSharingOn(ORG)).thenReturn(true);
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisation(anyLong())).thenAnswer(inv -> new AbdmOrganisation(inv.getArgument(0), "Organisation", "system-user-1"));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(orgContext).runAs(any(), any());
        EkaHipEvents hip = mock(EkaHipEvents.class);
        Bugsnag bugsnag = mock(Bugsnag.class);
        EkaEventWorker worker = new EkaEventWorker(inbox, new EkaEventGate(access, mock(AbdmHiuStore.class)), orgContext,
                mock(AbdmMessageLog.class), scheduler, bugsnag, provider(hip), provider(null), provider(null));
        ReflectionTestUtils.setField(worker, "inboxCron", EVERY_SECOND);
        ReflectionTestUtils.setField(worker, "sweepCron", EVERY_SECOND);
        stored("f12-stored", "RECEIVED", 0, null);
        stored("f12-stuck-on-last-try", "IN_PROGRESS", AbdmInbox.MAX_ATTEMPTS, "now() - interval '20 minutes'");
        stored("f12-stuck", "IN_PROGRESS", 1, "now() - interval '20 minutes'");

        worker.start();

        assertEquals(2, scheduler.scheduledCount());
        awaitStatus("f12-stored", AbdmInbox.DONE);
        awaitStatus("f12-stuck-on-last-try", AbdmInbox.FAILED);
        awaitStatus("f12-stuck", AbdmInbox.DONE);
        assertTrue(lastError("f12-stuck-on-last-try"), lastError("f12-stuck-on-last-try").startsWith("Left in progress"));
        verify(hip, times(2)).linkCareContext(any(JsonNode.class));
        verify(bugsnag, atLeastOnce()).notify(any(IllegalStateException.class));
    }

    // AbdmHousekeepingJob.start: the traffic purge, the consent expiry and retry, and visit linking each run on the scheduler.
    @Test
    public void theHousekeepingJobStartedOnTheSchedulerRunsEachOfItsTasks() {
        AbdmJobRunner runner = mock(AbdmJobRunner.class);
        AbdmHousekeepingJob job = new AbdmHousekeepingJob(runner, scheduler, mock(AbdmSettings.class), mock(AbdmMessageLog.class), inbox,
                mock(AbdmHiuDataService.class), mock(AbdmHiuStore.class), mock(AbdmHipStore.class), mock(EkaHipService.class),
                mock(EkaHiuService.class), Clock.systemUTC(), new AbdmConfig("", 30));
        for (String cron : new String[]{"trafficCron", "consentCron", "linkCron"}) ReflectionTestUtils.setField(job, cron, EVERY_SECOND);

        job.start();

        assertEquals(3, scheduler.scheduledCount());
        verify(runner, timeout(WAIT_MS).atLeastOnce()).forOrganisations(eq(AbdmHousekeepingJob.TRAFFIC_PURGE), any(), any(), any(), any());
        verify(runner, timeout(WAIT_MS).atLeastOnce()).forOrganisations(eq(AbdmHousekeepingJob.CONSENT_EXPIRY), any(), any(), any(), any());
        verify(runner, timeout(WAIT_MS).atLeastOnce()).forEachOrganisation(eq(AbdmHousekeepingJob.CONSENT_UPDATE_RETRY), any(), any());
        verify(runner, timeout(WAIT_MS).atLeastOnce()).forEachOrganisation(eq(AbdmHousekeepingJob.VISIT_LINKING), any(), any());
    }

    // Both start when the server is ready; without the listener neither would ever be scheduled.
    @Test
    public void bothStartWhenTheServerIsReady() throws NoSuchMethodException {
        for (Method start : new Method[]{EkaEventWorker.class.getMethod("start"), AbdmHousekeepingJob.class.getMethod("start")}) {
            EventListener listener = start.getAnnotation(EventListener.class);
            assertNotNull(start.getDeclaringClass().getSimpleName(), listener);
            assertTrue(start.getDeclaringClass().getSimpleName(),
                    Arrays.asList(listener.value()).contains(ApplicationReadyEvent.class)
                            || Arrays.asList(listener.classes()).contains(ApplicationReadyEvent.class));
        }
    }

    private void stored(String eventId, String status, int attempts, String claimedAt) {
        jdbc.update("insert into abdm_inbound_event (organisation_id, source, event_id, event_name, route, body, status, attempts, " +
                        "next_attempt_at, received_at, claimed_at, updated_at) values (?, 'eka', ?, 'abha.link_care_context', 'LINK_CARE_CONTEXT', " +
                        "'{\"n\":\"" + eventId + "\"}', ?, ?, now() - interval '1 second', now(), " + (claimedAt == null ? "null" : claimedAt) + ", now())",
                ORG, eventId, status, attempts);
    }

    private void awaitStatus(String eventId, String status) throws InterruptedException {
        long until = System.currentTimeMillis() + WAIT_MS;
        String now = statusOf(eventId);
        while (!status.equals(now) && System.currentTimeMillis() < until) {
            Thread.sleep(100);
            now = statusOf(eventId);
        }
        assertEquals(eventId, status, now);
    }

    private String statusOf(String eventId) {
        return jdbc.queryForObject("select status from abdm_inbound_event where event_id = ?", String.class, eventId);
    }

    private String lastError(String eventId) {
        return jdbc.queryForObject("select last_error from abdm_inbound_event where event_id = ?", String.class, eventId);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T handler) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(handler);
        return provider;
    }
}
