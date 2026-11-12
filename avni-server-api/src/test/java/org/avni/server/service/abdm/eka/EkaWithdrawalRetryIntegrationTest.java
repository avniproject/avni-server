package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmOrganisation;
import org.avni.server.service.abdm.AbdmScheduler;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// F1 against Postgres: a patient's withdrawal whose handling keeps failing is never given up. It keeps its body past the
// cap, alarms each time it fails there, and is applied once the cause clears. The real inbox; the handler stands in.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class EkaWithdrawalRetryIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long ORG = 2L;
    // Far ahead of the real clock, so no drain at the real time could take the row.
    private static final Instant T0 = Instant.parse("2100-01-01T10:00:00Z");
    private static final String REVOKED = "{\"event\":\"abha.consent_update\",\"transaction_id\":\"revoke-1\",\"data\":{\"status\":\"REVOKED\"," +
            "\"notification\":{\"consentRequestId\":\"cr-1\",\"status\":\"REVOKED\",\"consentArtefacts\":{\"id\":\"artefact-1\"}}}}";

    @Autowired
    private AbdmInbox inbox;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final EkaHiuEvents hiu = mock(EkaHiuEvents.class);
    private final EkaEventWorkerTest.MovingClock clock = new EkaEventWorkerTest.MovingClock(T0);
    private final List<JsonNode> applied = new ArrayList<>();
    private EkaEventWorker worker;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        super.setUp();
        jdbc.update("delete from abdm_inbound_event", Map.of());
        AbdmAccess access = mock(AbdmAccess.class);
        when(access.recordSharingOn(ORG)).thenReturn(true);
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisation(ORG)).thenReturn(new AbdmOrganisation(ORG, "Org two", "system-2"));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(orgContext).runAs(any(), any());
        ObjectProvider<EkaHiuEvents> hiuProvider = mock(ObjectProvider.class);
        when(hiuProvider.getIfAvailable()).thenReturn(hiu);
        worker = new EkaEventWorker(inbox, new EkaEventGate(access, mock(AbdmHiuStore.class)), orgContext, mock(AbdmMessageLog.class),
                mock(AbdmScheduler.class), bugsnag, mock(ObjectProvider.class), hiuProvider, mock(ObjectProvider.class), clock);
    }

    @Test
    public void aWithdrawalThatFailsPastTheCapKeepsItsBody_andIsAppliedOnceTheCauseClears() {
        int failures = AbdmInbox.MAX_ATTEMPTS + 2;
        AtomicInteger tries = new AtomicInteger();
        doAnswer(inv -> {
            if (tries.incrementAndGet() <= failures) throw new IllegalStateException("database down");
            applied.add(inv.getArgument(0));
            return null;
        }).when(hiu).consentUpdate(any());
        inbox.receive(ORG, "eka", "revoke-1", "abha.consent_update", "CONSENT_UPDATE", "cr-1", REVOKED, T0);

        for (int attempt = 1; attempt <= failures; attempt++) {
            assertEquals(1, worker.drain());
            Map<String, Object> row = row();
            assertEquals("RECEIVED", row.get("status"));
            assertEquals(REVOKED, row.get("body"));
            assertEquals(attempt, row.get("attempts"));
            clock.set(((Timestamp) row.get("next_attempt_at")).toInstant());
        }
        // An alarm for each try from the cap on, none before.
        verify(bugsnag, times(failures - AbdmInbox.MAX_ATTEMPTS + 1)).notify(any(EkaEventWorker.WithdrawalNotApplied.class));

        assertEquals(1, worker.drain());

        assertEquals("DONE", row().get("status"));
        assertEquals(1, applied.size());
        assertEquals("REVOKED", applied.get(0).at("/data/notification/status").asText());
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("select status, body, attempts, next_attempt_at from abdm_inbound_event where organisation_id = :org " +
                "and event_id = 'revoke-1'", new MapSqlParameterSource("org", ORG));
    }
}
