package org.avni.server.service.abdm.hiu;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.framework.security.UserContextHolder;
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

    // The hourly expiry and erase visits every organisation with an HIU consent still open or a record still held,
    // whatever its switches, and no other. Read across organisations, as the job runner reads before acting as each.
    @Test
    public void theOrganisationsHoldingConsentsOrRecordsAreThoseWithAnOpenConsentOrAKeptRecord() {
        var jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        jdbc.update("delete from abdm_consent_status");
        jdbc.update("delete from abdm_consent");
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values ('c-1', 2, 'HIU', 'cr-1', 'GRANTED')");
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values ('c-2', 3, 'HIU', 'cr-2', 'EXPIRED')");
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values ('c-3', 1, 'HIU', 'cr-3', 'DENIED')");
        jdbc.update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status) values ('f-1', 3, 'HIU', 'artefact-2', 'RECEIVED')");
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (3, (select id from abdm_data_flow where uuid = 'f-1'), 'tx-2', 'artefact-2')");

        assertEquals(List.of(2L, 3L), store.organisationsHoldingConsentsOrRecords());

        jdbc.update("update abdm_consent set status = 'REQUESTED' where uuid = 'c-3'");
        assertEquals(List.of(1L, 2L, 3L), store.organisationsHoldingConsentsOrRecords());
    }

    // F1: an ending applied with no user logged in names the organisation. Its request and artefact end, what was received
    // under them goes, an update parked earlier for the request is superseded, and another organisation holding the same
    // ids is left alone.
    @Test
    public void aConsentsEndingIsAppliedWithNoUser_forTheNamedOrganisationOnly() {
        var jdbc = namedJdbc.getJdbcOperations();
        clearHiu(jdbc);
        for (long organisationId : new long[]{2L, 3L}) held(jdbc, organisationId);
        jdbc.update("insert into abdm_parked_event (organisation_id, kind, reference, status, detail) values " +
                "(2, 'consent_update', 'cr-1', 'GRANTED', '{\"artefacts\":[\"artefact-9\"]}'), " +
                "(3, 'consent_update', 'cr-1', 'GRANTED', '{\"artefacts\":[\"artefact-9\"]}')");
        UserContextHolder.clear();

        assertTrue(store.endWithoutLogin(2L, "cr-1", List.of("artefact-1"), "REVOKED"));

        assertEquals(List.of("REVOKED", "REVOKED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 2 order by id", String.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = 2", Integer.class));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("select count(*) from abdm_consent_status where organisation_id = 2 and status = 'REVOKED'",
                Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_parked_event where organisation_id = 2 and resolved_at is null",
                Integer.class));
        assertEquals(List.of("GRANTED", "GRANTED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 3 order by id", String.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = 3", Integer.class));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from abdm_parked_event where organisation_id = 3 and resolved_at is null",
                Integer.class));
    }

    // An ending naming only artefacts ends the requests they were granted under, as the logged-in path does.
    @Test
    public void anEndingNamingOnlyArtefactsEndsTheRequestTheyWereGrantedUnder() {
        var jdbc = namedJdbc.getJdbcOperations();
        clearHiu(jdbc);
        held(jdbc, 2L);

        assertTrue(store.endWithoutLogin(2L, null, List.of("artefact-1"), "EXPIRED"));

        assertEquals(List.of("EXPIRED", "EXPIRED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 2 order by id", String.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = 2", Integer.class));
    }

    // What only a logged-in try can place (a request not mapped yet, an artefact a parked grant will make) waits for it,
    // which can park it or ask Eka; what is held is still ended and erased now.
    @Test
    public void anEndingNamingWhatOnlyALoggedInTryCanPlaceIsNotWhollyApplied_butWhatIsHeldStillEnds() {
        var jdbc = namedJdbc.getJdbcOperations();
        clearHiu(jdbc);
        held(jdbc, 2L);
        jdbc.update("insert into abdm_parked_event (organisation_id, kind, reference, status, detail) values " +
                "(2, 'consent_update', 'cr-parked', 'GRANTED', '{\"artefacts\":[\"artefact-parked\"]}')");

        assertFalse(store.endWithoutLogin(2L, "cr-unmapped", List.of(), "REVOKED"));
        assertFalse(store.endWithoutLogin(2L, null, List.of("artefact-1", "artefact-parked"), "REVOKED"));

        assertEquals(List.of("REVOKED", "REVOKED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 2 order by id", String.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = 2", Integer.class));
    }

    // Fix round 1: artefacts held nowhere and named by no parked grant are ignored, as the logged-in path ignores them, so
    // such an ending is done rather than retried hourly as a withdrawal not applied.
    @Test
    public void anEndingNamingOnlyArtefactsHeldNowhereIsDone_asTheLoggedInPathIgnoresThem() {
        var jdbc = namedJdbc.getJdbcOperations();
        clearHiu(jdbc);
        held(jdbc, 2L);
        jdbc.update("insert into abdm_parked_event (organisation_id, kind, reference, status, detail) values " +
                "(3, 'consent_update', 'cr-other', 'GRANTED', '{\"artefacts\":[\"artefact-unknown\"]}')");

        assertTrue(store.endWithoutLogin(2L, null, List.of("artefact-unknown"), "REVOKED"));
        assertTrue(store.endWithoutLogin(2L, null, List.of("artefact-1", "artefact-unknown"), "REVOKED"));

        assertEquals(List.of("REVOKED", "REVOKED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 2 order by id", String.class));
    }

    private static void clearHiu(org.springframework.jdbc.core.JdbcOperations jdbc) {
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        jdbc.update("delete from abdm_consent_status");
        jdbc.update("delete from abdm_consent");
        jdbc.update("delete from abdm_parked_event");
    }

    // A granted request cr-1 with artefact-1 and one record received under it.
    private static void held(org.springframework.jdbc.core.JdbcOperations jdbc, long organisationId) {
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values (?, ?, 'HIU', 'cr-1', 'GRANTED')",
                "r-" + organisationId, organisationId);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, consent_id, status) values (?, ?, 'HIU', 'cr-1', 'artefact-1', 'GRANTED')",
                "a-" + organisationId, organisationId);
        jdbc.update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status) values (?, ?, 'HIU', 'artefact-1', 'RECEIVED')",
                "f-" + organisationId, organisationId);
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (?, (select id from abdm_data_flow where uuid = ?), 'tx-1', 'artefact-1')", organisationId, "f-" + organisationId);
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

    // A request Eka gave no clear answer to is UNKNOWN, with why; settled once, by whichever path matches it first.
    @Test
    public void anUnknownRequestIsListedUntilSettledOnce() {
        for (String uuid : List.of("u-unknown", "u-requested")) {
            store.insertConsentRequest(uuid, "shyam@sbx", "HIU-1", "CAREMGT", "OPConsultation", null, null, null, "init-" + uuid, "REQUESTED");
        }
        store.markConsentUnknown(idOf("u-unknown"), "Eka did not answer");

        assertEquals(List.of("u-unknown"), store.unknownConsentRequests().stream().map(AbdmHiuStore.Consent::uuid).toList());
        assertEquals("UNKNOWN", store.findConsentRequestByUuid("u-unknown").orElseThrow().status());
        assertEquals("Eka did not answer", store.findConsentRequestByUuid("u-unknown").orElseThrow().error());

        assertTrue(store.settleUnknownConsent(idOf("u-unknown"), "abdm-A"));
        assertFalse(store.settleUnknownConsent(idOf("u-unknown"), "abdm-B"));

        AbdmHiuStore.Consent settled = store.findConsentRequest("abdm-A").orElseThrow();
        assertEquals("u-unknown", settled.uuid());
        assertEquals("REQUESTED", settled.status());
        assertEquals(null, settled.error());
        assertTrue(store.unknownConsentRequests().isEmpty());
        assertEquals(List.of("REQUESTED", "UNKNOWN", "REQUESTED"), namedJdbc.getJdbcOperations().queryForList("select s.status " +
                "from abdm_consent_status s join abdm_consent c on c.id = s.consent_row_id where c.uuid = 'u-unknown' order by s.id", String.class));
    }

    // An UNKNOWN request is still open: past its expiry it expires, and the hourly job visits its organisation.
    @Test
    public void anUnknownRequestExpiresAndKeepsItsOrganisationVisited() {
        var jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("delete from abdm_health_information");
        jdbc.update("delete from abdm_data_flow");
        jdbc.update("delete from abdm_consent_status");
        jdbc.update("delete from abdm_consent");
        store.insertConsentRequest("u-1", "shyam@sbx", "HIU-1", "CAREMGT", "OPConsultation", null, null,
                java.time.Instant.now().minusSeconds(60), "init-u-1", "REQUESTED");
        store.markConsentUnknown(idOf("u-1"), "Eka did not answer");

        assertEquals(List.of(2L), store.organisationsHoldingConsentsOrRecords());
        assertTrue(store.holdsConsentsOrRecords(2L));

        assertEquals(1, store.expireOverdueConsents());
        assertEquals("EXPIRED", store.findConsentRequestByUuid("u-1").orElseThrow().status());
    }

    private long idOf(String uuid) {
        return store.findConsentRequestByUuid(uuid).orElseThrow().id();
    }
}
