package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

// The webhook inbox (abdm_inbound_event) against Postgres: an event is stored before Eka is answered, taken by one worker
// at a time, tried again after a growing wait until a cap, and swept back when a worker died holding it. A consent's
// updates are taken in the order they came, and nothing about the queue is held in memory.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmInboxIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long ORG = 2L;
    private static final long OTHER_ORG = 3L;
    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    private AbdmInbox inbox;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private AbdmOrgContext orgContext;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jdbc.update("delete from abdm_inbound_event", Map.of());
    }

    @Test
    public void anEventIsStoredAsReceived_withItsBody_beforeAnythingHandlesIt() {
        assertTrue(receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0));

        Map<String, Object> row = row("e-1");
        assertEquals("RECEIVED", row.get("status"));
        assertEquals(0, row.get("attempts"));
        assertEquals("{\"n\":\"e-1\"}", row.get("body"));
        assertEquals("abha.event", row.get("event_name"));
        assertEquals(Timestamp.from(T0), row.get("next_attempt_at"));
    }

    @Test
    public void anEventIdIsStoredOncePerOrganisation() {
        assertTrue(receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0));
        assertFalse(receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0));
        assertTrue(receive(OTHER_ORG, "e-1", "LINK_CARE_CONTEXT", null, T0));

        assertEquals(2, inbox.claim(10, T0).size());
    }

    @Test
    public void dueEventsAreTakenOldestFirst_onceEach_andCountedAsATry() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        receive(ORG, "e-2", "HIP_DATA_FETCH", null, T0);
        receive(ORG, "e-3", "LINK_CARE_CONTEXT", null, T0.plusSeconds(60));

        List<AbdmInbox.Event> taken = inbox.claim(10, T0);

        assertEquals(List.of("e-1", "e-2"), taken.stream().map(AbdmInbox.Event::eventId).toList());
        assertEquals(1, taken.get(0).attempts());
        assertEquals(ORG, taken.get(0).organisationId());
        assertEquals("{\"n\":\"e-1\"}", taken.get(0).body());
        assertEquals("IN_PROGRESS", row("e-1").get("status"));
        assertTrue(inbox.claim(10, T0).isEmpty());
    }

    @Test
    public void aHandledEventIsDone_andItsBodyIsNotKept() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        AbdmInbox.Event taken = inbox.claim(10, T0).get(0);

        inbox.done(taken.id());

        assertEquals("DONE", row("e-1").get("status"));
        assertNull(row("e-1").get("body"));
        assertTrue(inbox.claim(10, T0.plus(Duration.ofDays(1))).isEmpty());
    }

    @Test
    public void aFailedEventIsTriedAgainAfterAGrowingWait_thenFailsAtTheCap() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        Instant now = T0;
        Duration previousWait = Duration.ZERO;
        for (int attempt = 1; attempt < AbdmInbox.MAX_ATTEMPTS; attempt++) {
            AbdmInbox.Event taken = inbox.claim(10, now).get(0);
            assertEquals(attempt, taken.attempts());

            assertFalse(inbox.retry(taken, "IllegalStateException: database down", now));

            Map<String, Object> row = row("e-1");
            assertEquals("RECEIVED", row.get("status"));
            assertEquals("IllegalStateException: database down", row.get("last_error"));
            Duration wait = Duration.between(now, ((Timestamp) row.get("next_attempt_at")).toInstant());
            assertTrue(wait + " after " + previousWait, wait.compareTo(previousWait) >= 0 && wait.compareTo(AbdmInbox.MAX_WAIT) <= 0);
            assertTrue(inbox.claim(10, now.plus(wait).minusSeconds(1)).isEmpty());
            previousWait = wait;
            now = now.plus(wait);
        }
        AbdmInbox.Event last = inbox.claim(10, now).get(0);
        assertEquals(AbdmInbox.MAX_ATTEMPTS, last.attempts());

        assertTrue(inbox.retry(last, "IllegalStateException: database down", now));

        assertEquals("FAILED", row("e-1").get("status"));
        assertNotNull(row("e-1").get("body"));
        assertTrue(inbox.claim(10, now.plus(Duration.ofDays(1))).isEmpty());
    }

    @Test
    public void anEventGivenUpOnIsNotTakenAgain() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        AbdmInbox.Event taken = inbox.claim(10, T0).get(0);

        inbox.fail(taken.id(), "Record sharing is off for the organisation", T0);

        assertEquals("FAILED", row("e-1").get("status"));
        assertTrue(inbox.claim(10, T0.plus(Duration.ofDays(1))).isEmpty());
    }

    // A worker that died holding an event (a restart, a crash) leaves it in progress; the sweep puts it back.
    @Test
    public void anEventStuckInProgressIsSweptBack_andOneStillBeingWorkedIsLeft() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        receive(ORG, "e-2", "LINK_CARE_CONTEXT", null, T0);
        inbox.claim(1, T0);
        Instant later = T0.plus(AbdmInbox.IN_PROGRESS_TIMEOUT).plusSeconds(1);
        inbox.claim(1, later);

        AbdmInbox.Sweep sweep = inbox.sweep(later);

        assertEquals(1, sweep.returned());
        assertEquals(0, sweep.failed());
        assertEquals("RECEIVED", row("e-1").get("status"));
        assertEquals("IN_PROGRESS", row("e-2").get("status"));
        assertEquals(List.of("e-1"), inbox.claim(10, later).stream().map(AbdmInbox.Event::eventId).toList());
    }

    @Test
    public void anEventStuckInProgressOnItsLastTryFailsWhenSwept() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);
        jdbc.update("update abdm_inbound_event set attempts = :max - 1 where event_id = 'e-1'", Map.of("max", AbdmInbox.MAX_ATTEMPTS));
        inbox.claim(1, T0);

        AbdmInbox.Sweep sweep = inbox.sweep(T0.plus(AbdmInbox.IN_PROGRESS_TIMEOUT).plusSeconds(1));

        assertEquals(1, sweep.failed());
        assertEquals("FAILED", row("e-1").get("status"));
    }

    // A data push waits while an update of its consent that came before it is still to be handled, so it finds its grant.
    @Test
    public void aConsentsDataPushWaitsForAnUpdateOfThatConsentThatCameFirst() {
        receive(ORG, "update-1", "CONSENT_UPDATE", "cr-1", T0);
        receive(ORG, "push-1", "HIU_DATA_PUSH", "cr-1", T0.plusSeconds(1));
        receive(ORG, "push-2", "HIU_DATA_PUSH", "cr-2", T0.plusSeconds(2));

        List<AbdmInbox.Event> first = inbox.claim(10, T0.plusSeconds(5));

        assertEquals(List.of("update-1", "push-2"), first.stream().map(AbdmInbox.Event::eventId).toList());
        assertTrue(inbox.claim(10, T0.plusSeconds(5)).isEmpty());

        inbox.done(first.get(0).id());

        assertEquals(List.of("push-1"), inbox.claim(10, T0.plusSeconds(5)).stream().map(AbdmInbox.Event::eventId).toList());
    }

    @Test
    public void anUpdateWaitingForItsNextTryStillHoldsBackTheLaterEventsOfItsConsent() {
        receive(ORG, "update-1", "CONSENT_UPDATE", "cr-1", T0);
        receive(ORG, "update-2", "CONSENT_UPDATE", "cr-1", T0.plusSeconds(1));
        AbdmInbox.Event update = inbox.claim(10, T0).get(0);
        inbox.retry(update, "IllegalStateException: database down", T0);

        assertTrue(inbox.claim(10, T0.plusSeconds(2)).isEmpty());
    }

    // A push that came before its grant does not hold the grant back, or neither would ever be handled.
    @Test
    public void aDataPushThatCameFirstDoesNotHoldBackTheUpdateItWaitsFor() {
        receive(ORG, "push-1", "HIU_DATA_PUSH", "cr-1", T0);
        receive(ORG, "update-1", "CONSENT_UPDATE", "cr-1", T0.plusSeconds(1));
        AbdmInbox.Event push = inbox.claim(1, T0.plusSeconds(1)).get(0);
        inbox.retry(push, "AbdmNotReadyException: no grant yet", T0.plusSeconds(1));

        assertEquals(List.of("update-1"), inbox.claim(10, T0.plusSeconds(1)).stream().map(AbdmInbox.Event::eventId).toList());
    }

    @Test
    public void anotherOrganisationsUpdateOfTheSameConsentIdHoldsNothingBack() {
        receive(OTHER_ORG, "update-1", "CONSENT_UPDATE", "cr-1", T0);
        receive(ORG, "push-1", "HIU_DATA_PUSH", "cr-1", T0.plusSeconds(1));

        assertEquals(2, inbox.claim(10, T0.plusSeconds(1)).size());
    }

    // Nothing about the queue lives in memory: a new inbox, as after a restart, takes what the old one stored.
    @Test
    public void storedEventsSurviveARestart() {
        receive(ORG, "e-1", "LINK_CARE_CONTEXT", null, T0);

        AbdmInbox afterRestart = new AbdmInbox(jdbc, orgContext);

        assertEquals(List.of("e-1"), afterRestart.claim(10, T0).stream().map(AbdmInbox.Event::eventId).toList());
    }

    @Test
    public void finishedEventsOlderThanTheRetentionAreDeletedForTheOrganisationOnly() {
        receive(ORG, "old-done", "LINK_CARE_CONTEXT", null, T0.minus(Duration.ofDays(40)));
        receive(ORG, "old-waiting", "LINK_CARE_CONTEXT", null, T0.minus(Duration.ofDays(40)));
        receive(ORG, "new-done", "LINK_CARE_CONTEXT", null, T0);
        receive(OTHER_ORG, "old-done", "LINK_CARE_CONTEXT", null, T0.minus(Duration.ofDays(40)));
        jdbc.update("update abdm_inbound_event set status = 'DONE' where event_id in ('old-done', 'new-done')", Map.of());
        setUser("demo-user");

        assertEquals(1, inbox.purgeFinishedOlderThan(T0.minus(Duration.ofDays(30))));

        assertEquals(List.of("new-done", "old-waiting"), jdbc.queryForList("select event_id from abdm_inbound_event " +
                "where organisation_id = :org order by event_id", Map.of("org", ORG), String.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from abdm_inbound_event where organisation_id = :org",
                Map.of("org", OTHER_ORG), Integer.class));
    }

    private boolean receive(long organisationId, String eventId, String route, String consentKey, Instant at) {
        return inbox.receive(organisationId, "eka", eventId, "abha.event", route, consentKey, "{\"n\":\"" + eventId + "\"}", at);
    }

    private Map<String, Object> row(String eventId) {
        return jdbc.queryForMap("select * from abdm_inbound_event where organisation_id = :org and event_id = :id",
                new MapSqlParameterSource("org", ORG).addValue("id", eventId));
    }
}
