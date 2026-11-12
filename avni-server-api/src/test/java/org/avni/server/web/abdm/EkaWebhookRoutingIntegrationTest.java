package org.avni.server.web.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.service.abdm.AbdmPaths;
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

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

// Eka's webhook end to end: over HTTP with no Avni user, through the security filters, to the organisation the address
// names and into the database before the 200. Not @Transactional: the server's own threads must see the rows.
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
    public void aSignedEventIsInTheDatabaseWhenEkaIsAnswered_andIsThenHandledAsTheOrganisation() throws InterruptedException {
        String body = "{\"event\":\"abha.routing_check\",\"transaction_id\":\"routing-1\",\"data\":{}}";

        ResponseEntity<String> response = post(WEBHOOK, body, signed(body));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> stored = jdbc.queryForList("select status from abdm_inbound_event where organisation_id = ? and event_id = 'routing-1'",
                String.class, ORG);
        assertEquals(1, stored.size());
        assertEquals("DONE", awaitStatus("routing-1", "DONE"));
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

    // The worker is woken by the webhook and runs on ABDM's own threads; the test waits for it so nothing is left running.
    private String awaitStatus(String eventId, String wanted) throws InterruptedException {
        String status = null;
        for (int i = 0; i < 100; i++) {
            status = jdbc.queryForObject("select status from abdm_inbound_event where organisation_id = ? and event_id = ?", String.class,
                    ORG, eventId);
            if (wanted.equals(status)) return status;
            Thread.sleep(100);
        }
        return status;
    }
}
