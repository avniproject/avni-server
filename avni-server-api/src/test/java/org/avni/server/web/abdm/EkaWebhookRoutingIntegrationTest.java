package org.avni.server.web.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmScheduler;
import org.avni.server.service.abdm.AbdmTestScheduling;
import org.avni.server.service.abdm.eka.EkaEventWorker;
import org.avni.server.service.abdm.eka.EkaWebhookVerifier;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

// Eka's webhook end to end: over HTTP with no Avni user, through the security filters, to the organisation the address
// names and into the database before the 200. The worker the webhook wakes runs nothing in tests (AbdmTestScheduling):
// each test drains the inbox itself, so no drain outlives it. Not @Transactional: the worker reads committed rows.
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class EkaWebhookRoutingIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long ORG = 2L;
    private static final String WEBHOOK = "webhook-routing-test";
    private static final String KEY = "routing-test-signing-key";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private EkaCredentialCipher cipher;
    @Autowired
    private ExternalSystemConfigRepository configRepository;
    @Autowired
    private EkaEventWorker worker;
    @Autowired
    private AbdmScheduler scheduler;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private AbdmOrgContext orgContext;
    private JdbcTemplate jdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jdbc = new JdbcTemplate(dataSource);
        switches(true);
        jdbc.update("insert into external_system_config (organisation_id, uuid, is_voided, version, created_by_id, last_modified_by_id, " +
                        "created_date_time, last_modified_date_time, system_name, config) values (?, ?, false, 0, 1, 1, now(), now(), 'Eka', " +
                        "jsonb_build_object('environment', 'sandbox', 'clientId', 'm1-client', 'hipIds', jsonb_build_array('hip-routing'), " +
                        "'sharingClientId', 'sharing-client', 'encryptedSharingClientSecret', ?, 'webhookId', ?, " +
                        "'encryptedWebhookSigningKey', ?, 'avniSystemUser', 'demo-admin'))",
                ORG, UUID.randomUUID().toString(), cipher.encrypt("sharing-secret"), WEBHOOK, cipher.encrypt(KEY));
    }

    @Test
    public void theWebhookIdFindsItsOrganisationsEkaSettings_andAVoidedRowIsNotFound() {
        assertEquals(Long.valueOf(ORG), configRepository.findEkaSystemConfigByWebhookId(WEBHOOK).orElseThrow().getOrganisationId());
        assertTrue(configRepository.findEkaSystemConfigByWebhookId("no-such-webhook").isEmpty());

        jdbc.update("update external_system_config set is_voided = true where system_name = 'Eka' and organisation_id = ?", ORG);

        assertTrue(configRepository.findEkaSystemConfigByWebhookId(WEBHOOK).isEmpty());
    }

    @Test
    public void aSignedEventIsInTheDatabaseWhenEkaIsAnswered_andIsThenHandledAsTheOrganisation() {
        String body = "{\"event\":\"abha.routing_check\",\"transaction_id\":\"routing-1\",\"data\":{}}";
        int wakes = ((AbdmTestScheduling.ManualScheduler) scheduler).executeRequests();

        ResponseEntity<String> response = post(WEBHOOK, body, signed(body));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("RECEIVED"), statuses("routing-1"));
        assertEquals(wakes + 1, ((AbdmTestScheduling.ManualScheduler) scheduler).executeRequests());

        worker.drain();

        assertEquals(List.of("DONE"), statuses("routing-1"));
    }

    @Test
    public void anUnknownAddressIs404() {
        String body = "{\"event\":\"abha.routing_check\",\"transaction_id\":\"routing-2\"}";

        assertEquals(HttpStatus.NOT_FOUND, post("no-such-webhook", body, signed(body)).getStatusCode());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_inbound_event", Integer.class));
    }

    @Test
    public void aBadSignatureIs401AndSaysNothingMore() {
        String body = "{\"event\":\"abha.routing_check\",\"transaction_id\":\"routing-3\"}";

        ResponseEntity<String> response = post(WEBHOOK, body, "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("{\"status\":\"rejected\"}", response.getBody());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_inbound_event", Integer.class));
    }

    @Test
    public void anOrganisationWithRecordSharingOffIs404() {
        switches(false);
        String body = "{\"event\":\"abha.routing_check\",\"transaction_id\":\"routing-4\"}";

        assertEquals(HttpStatus.NOT_FOUND, post(WEBHOOK, body, signed(body)).getStatusCode());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_inbound_event", Integer.class));
    }

    // After record sharing is switched off, a patient's withdrawal is still taken, handled and its records erased;
    // a data push for the same organisation is refused.
    @Test
    public void withRecordSharingOffAWithdrawalIsHandledAndItsRecordsErased_butADataPushIsRefused() {
        switches(false);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values (?, ?, 'HIU', 'cr-1', 'GRANTED')",
                UUID.randomUUID().toString(), ORG);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, consent_id, status) " +
                "values (?, ?, 'HIU', 'cr-1', 'artefact-1', 'GRANTED')", UUID.randomUUID().toString(), ORG);
        jdbc.update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status) values ('flow-1', ?, 'HIU', 'artefact-1', 'RECEIVED')", ORG);
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (?, (select id from abdm_data_flow where uuid = 'flow-1'), 'tx-1', 'artefact-1')", ORG);
        String revocation = "{\"event\":\"abha.consent_update\",\"transaction_id\":\"revoke-1\",\"data\":{\"status\":\"REVOKED\"," +
                "\"notification\":{\"consentRequestId\":\"cr-1\",\"status\":\"REVOKED\",\"consentArtefacts\":{\"id\":\"artefact-1\"}}}}";
        String push = "{\"event\":\"abha.hiu_data_push\",\"transaction_id\":\"push-1\",\"data\":{\"consent_id\":\"cr-1\",\"entries\":[]}}";

        assertEquals(HttpStatus.OK, post(WEBHOOK, revocation, signed(revocation)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, post(WEBHOOK, push, signed(push)).getStatusCode());

        worker.drain();

        assertEquals(List.of("DONE"), statuses("revoke-1"));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = ?",
                Integer.class, ORG));
        assertEquals("REVOKED", jdbc.queryForObject("select status from abdm_consent where organisation_id = ? and consent_id = 'artefact-1'",
                String.class, ORG));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_inbound_event where event_id = 'push-1'", Integer.class));
    }

    // F1: the organisation's system user is voided, so no work can run as it; the patient's withdrawal still ends the
    // consent and erases what was received under it, with no login and only in that organisation.
    @Test
    public void withItsSystemUserVoidedAWithdrawalStillEndsTheConsentAndErasesItsRecords_inThatOrganisationOnly() {
        heldConsent(ORG);
        heldConsent(3L);
        assertEquals(1, jdbc.update("update users set is_voided = true where username = 'demo-admin'"));
        // Voided behind Hibernate's back, so its caches (query results too) must forget the user.
        entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getCache().evictAllRegions();
        assertThrows(AbdmOrgContext.SystemUserException.class, () -> orgContext.runAs(orgContext.organisation(ORG), () -> {
        }));
        String revocation = "{\"event\":\"abha.consent_update\",\"transaction_id\":\"revoke-2\",\"data\":{\"status\":\"REVOKED\"," +
                "\"notification\":{\"consentRequestId\":\"cr-1\",\"status\":\"REVOKED\",\"consentArtefacts\":{\"id\":\"artefact-1\"}}}}";

        assertEquals(HttpStatus.OK, post(WEBHOOK, revocation, signed(revocation)).getStatusCode());
        worker.drain();

        assertEquals(List.of("DONE"), statuses("revoke-2"));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = ?",
                Integer.class, ORG));
        assertEquals(List.of("REVOKED", "REVOKED"), jdbc.queryForList("select status from abdm_consent where organisation_id = ? order by id",
                String.class, ORG));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("select count(*) from abdm_health_information where organisation_id = 3",
                Integer.class));
        assertEquals(List.of("GRANTED", "GRANTED"), jdbc.queryForList("select status from abdm_consent where organisation_id = 3 order by id",
                String.class));
    }

    private void heldConsent(long organisationId) {
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, status) values (?, ?, 'HIU', 'cr-1', 'GRANTED')",
                UUID.randomUUID().toString(), organisationId);
        jdbc.update("insert into abdm_consent (uuid, organisation_id, role, consent_request_id, consent_id, status) " +
                "values (?, ?, 'HIU', 'cr-1', 'artefact-1', 'GRANTED')", UUID.randomUUID().toString(), organisationId);
        String flow = "flow-" + organisationId;
        jdbc.update("insert into abdm_data_flow (uuid, organisation_id, role, consent_id, status) values (?, ?, 'HIU', 'artefact-1', 'RECEIVED')",
                flow, organisationId);
        jdbc.update("insert into abdm_health_information (organisation_id, data_flow_id, transaction_id, consent_id) " +
                "values (?, (select id from abdm_data_flow where uuid = ?), 'tx-1', 'artefact-1')", organisationId, flow);
    }

    private void switches(boolean recordSharing) {
        jdbc.update("update organisation_config set settings = settings || jsonb_build_object('enableAbha', true, 'enableAbdmRecordSharing', ?) " +
                "where organisation_id = ?", recordSharing, ORG);
    }

    private ResponseEntity<String> post(String webhookId, String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(EkaWebhookVerifier.HEADER, signature);
        return template.postForEntity(AbdmPaths.IN_EKA_WEBHOOK + "/" + webhookId, new HttpEntity<>(body, headers), String.class);
    }

    private static String signed(String body) {
        String t = String.valueOf(Instant.now().getEpochSecond());
        return "t=" + t + ",v1=" + EkaWebhookVerifier.sign(KEY, t, body);
    }

    private List<String> statuses(String eventId) {
        return jdbc.queryForList("select status from abdm_inbound_event where organisation_id = ? and event_id = ?", String.class, ORG, eventId);
    }
}
