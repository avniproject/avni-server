package org.avni.server.service.abdm.hiu;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DEMO-ONLY abdm-hackathon: never merged.
// AbdmHiuStore's SQL against Postgres.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmHiuStoreIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private AbdmHiuStore store;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    // The hourly retry asks for each id with an update still parked, once, and leaves one parked over a week ago:
    // no request of ours took it in that time, so none will.
    @Test
    public void parkedConsentRequestIdsAreTheUnresolvedOnesOfTheLastWeekEachOnce() {
        store.parkConsentUpdate("abdm-A", "GRANTED", List.of("artefact-1"));
        store.parkConsentUpdate("abdm-A", "REVOKED", List.of("artefact-1"));
        store.parkConsentUpdate("abdm-B", "DENIED", List.of());
        store.parkConsentUpdate("abdm-C", "GRANTED", List.of("artefact-3"));
        store.parkConsentUpdate("abdm-D", "GRANTED", List.of("artefact-4"));
        store.parkedConsentUpdates("abdm-B").forEach(p -> store.claimParked(p.id()));
        namedJdbc.getJdbcOperations().update("update abdm_parked_event set created_at = now() - interval '8 days' where reference = 'abdm-C'");

        // Newest first, so an id that never matches cannot hold back the ones parked after it.
        assertEquals(List.of("abdm-D", "abdm-A"), store.parkedConsentRequestIds());
    }

    @Test
    public void aParkedUpdateIsClaimedOnceAndCanBeReleased() {
        store.parkConsentUpdate("abdm-A", "GRANTED", List.of("artefact-1"));
        long id = store.parkedConsentUpdates("abdm-A").get(0).id();

        assertTrue(store.claimParked(id));
        assertFalse(store.claimParked(id));
        assertTrue(store.parkedConsentUpdates("abdm-A").isEmpty());
        store.releaseParked(id);
        assertTrue(store.claimParked(id));
    }

    @Test
    public void theParkedUpdateNamingAnArtefactIsFoundByIt() {
        store.parkConsentUpdate("abdm-A", "GRANTED", List.of("artefact-1", "artefact-2"));
        store.parkConsentUpdate("abdm-B", "GRANTED", List.of("artefact-3"));
        store.parkedConsentUpdates("abdm-B").forEach(p -> store.claimParked(p.id()));

        assertEquals(Optional.of("abdm-A"), store.parkedReferenceOf("artefact-2"));
        assertEquals(Optional.empty(), store.parkedReferenceOf("artefact-3"));
        assertEquals(Optional.empty(), store.parkedReferenceOf("artefact-9"));
    }

    // Matching through Eka looks at every request still waiting for ABDM's id, not the newest 200 of all requests.
    @Test
    public void theRequestsWaitingAreTheRequestedOnesCarryingAnId() {
        for (String uuid : List.of("w-requested", "w-no-id", "w-granted", "w-failed")) {
            store.insertConsentRequest(uuid, "shyam@sbx", "HIU-1", "CAREMGT", "OPConsultation", null, null, null, "init-" + uuid, "REQUESTED");
        }
        store.setConsentRequestId(idOf("w-requested"), "eka-1", "REQUESTED");
        store.setConsentRequestId(idOf("w-granted"), "abdm-2", "GRANTED");
        store.setConsentRequestId(idOf("w-failed"), "eka-3", "REQUESTED");
        store.failConsent(idOf("w-failed"), "refused");

        assertEquals(List.of("w-requested"), store.waitingConsentRequests().stream().map(AbdmHiuStore.Consent::uuid).toList());
    }

    private long idOf(String uuid) {
        return store.findConsentRequestByUuid(uuid).orElseThrow().id();
    }
}
