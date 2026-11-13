package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Not @Transactional: each request runs on the server's own connection, and the organisation role is applied when a
 * connection is borrowed, so the row-level security checks need real commits. tear-down.sql cleans up around each test.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaAdminControllerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String DEMO = "ae0e4ac4-681d-45f2-8bdd-2b09a5a1a6e5";
    private static final Long DEMO_ID = 2L;
    private static final String A_DEMO = "2734f2ba-610b-49f8-b8d3-4196a460e325";
    private static final String PATH = "/abha/admin/ekaConfig?organisationUUID=";

    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;
    @Autowired
    private EkaCredentialCipher ekaCredentialCipher;
    @Autowired
    private DataSource dataSource;

    @Before
    public void actAsSuperAdmin() {
        setUser(userRepository.getDefaultSuperAdmin());
    }

    @Test
    public void nonSuperAdminIsRefused() {
        setUser("demo-admin");
        assertEquals(HttpStatus.FORBIDDEN, put(DEMO, config("test-hip"), null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(DEMO).getStatusCode());
    }

    @Test
    public void superAdminActingAsAnOrganisationIs400() {
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, config("test-hip"), DEMO).getStatusCode());
    }

    @Test
    public void organisationOutsideAdminsAccountIs404() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into account (name) select 'eka-test-other-account' where not exists (select 1 from account where name = 'eka-test-other-account')");
        Long accountId = jdbc.queryForObject("select id from account where name = 'eka-test-other-account'", Long.class);
        String otherOrganisation = UUID.randomUUID().toString();
        jdbc.update("insert into organisation (name, db_user, media_directory, uuid, parent_organisation_id, schema_name, category_id, status_id, account_id) " +
                "values ('eka-other', 'eka_other', 'eka-other', ?, 1, 'eka_other', 1, 1, ?)", otherOrganisation, accountId);

        assertEquals(HttpStatus.NOT_FOUND, put(otherOrganisation, config("test-hip"), null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, put(UUID.randomUUID().toString(), config("test-hip"), null).getStatusCode());
    }

    @Test
    public void rowLandsInTargetOrganisation() throws Exception {
        // Loaded as super admin: once acting as one organisation, the other's users are filtered out.
        User demoAdmin = userRepository.findByUsername("demo-admin");
        User aDemoAdmin = userRepository.findByUsername("a-demo-admin");
        assertEquals(HttpStatus.OK, put(DEMO, config("test-hip"), null).getStatusCode());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(DEMO_ID, jdbc.queryForObject("select organisation_id from public.external_system_config where system_name = 'Eka'", Long.class));
        assertNotNull(jdbc.queryForObject("select uuid from public.external_system_config where system_name = 'Eka'", String.class));

        setUser(demoAdmin);
        EkaSystemConfig asDemo = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals("client-1", asDemo.getClientId());

        setUser(aDemoAdmin);
        assertNull("another organisation must not see the row", externalSystemConfigRepository.findBySystemNameAndOrganisationId(SystemName.Eka, DEMO_ID));
    }

    @Test
    public void savedRowHoldsNoPlaintextSecret() throws Exception {
        assertEquals(HttpStatus.OK, put(DEMO, config("test-hip"), null).getStatusCode());

        String stored = storedConfig();
        assertFalse(stored, stored.contains("test-secret-value"));
        assertFalse(stored, stored.contains("test-signing-key"));
        EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals("test-secret-value", ekaCredentialCipher.decrypt(config.getEncryptedClientSecret()));
        assertEquals("test-signing-key", ekaCredentialCipher.decrypt(config.getEncryptedWebhookSigningKey()));
    }

    @Test
    public void getHidesSecrets() throws Exception {
        put(DEMO, config("test-hip", "test-hip-2"), null);

        ResponseEntity<String> response = get(DEMO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals("sandbox", body.get("environment").asText());
        assertEquals("client-1", body.get("clientId").asText());
        assertEquals("[\"test-hip\",\"test-hip-2\"]", body.get("hipIds").toString());
        assertEquals("demo-admin", body.get("avniSystemUser").asText());
        assertTrue(body.get("clientSecretSet").asBoolean());
        assertTrue(body.get("webhookSigningKeySet").asBoolean());
        assertFalse(response.getBody(), response.getBody().contains("test-secret-value"));
        assertFalse(response.getBody(), response.getBody().contains("test-signing-key"));
        assertFalse(response.getBody(), response.getBody().toLowerCase().contains("encrypted"));
    }

    @Test
    public void getWhenNotConfiguredIs404() {
        assertEquals(HttpStatus.NOT_FOUND, get(DEMO).getStatusCode());
    }

    @Test
    public void omittedSecretKeepsStoredValue() throws Exception {
        put(DEMO, config("test-hip"), null);
        EkaSystemConfig first = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);

        Map<String, Object> withoutSecrets = config("test-hip");
        withoutSecrets.remove("clientSecret");
        withoutSecrets.remove("webhookSigningKey");
        withoutSecrets.put("hipIds", Arrays.asList("test-hip", "test-hip-3"));
        assertEquals(HttpStatus.OK, put(DEMO, withoutSecrets, null).getStatusCode());
        EkaSystemConfig second = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals(Arrays.asList("test-hip", "test-hip-3"), second.getHipIds());
        assertEquals(first.getEncryptedClientSecret(), second.getEncryptedClientSecret());
        assertEquals(first.getEncryptedWebhookSigningKey(), second.getEncryptedWebhookSigningKey());

        Map<String, Object> newSecret = config("test-hip");
        newSecret.put("clientSecret", "rotated-secret-value");
        put(DEMO, newSecret, null);
        assertEquals("rotated-secret-value", ekaCredentialCipher.decrypt(externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID).getEncryptedClientSecret()));

        assertEquals(Integer.valueOf(1), new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.external_system_config where system_name = 'Eka' and organisation_id = ?", Integer.class, DEMO_ID));
    }

    // The stored secret belongs to the stored client and environment; keeping it for another would fail every login at Eka.
    @Test
    public void changingClientOrEnvironmentWithoutSecretIs400() {
        put(DEMO, config("test-hip"), null);

        Map<String, Object> otherClient = config("test-hip");
        otherClient.remove("clientSecret");
        otherClient.put("clientId", "client-2");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherClient, null).getStatusCode());

        Map<String, Object> otherEnvironment = config("test-hip");
        otherEnvironment.remove("clientSecret");
        otherEnvironment.put("environment", "production");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherEnvironment, null).getStatusCode());

        Map<String, Object> otherClientWithSecret = config("test-hip");
        otherClientWithSecret.put("clientId", "client-2");
        assertEquals(HttpStatus.OK, put(DEMO, otherClientWithSecret, null).getStatusCode());
    }

    @Test
    public void omittedAvniSystemUserKeepsStoredValue() throws Exception {
        put(DEMO, config("test-hip"), null);

        Map<String, Object> withoutUser = config("test-hip");
        withoutUser.remove("avniSystemUser");
        assertEquals(HttpStatus.OK, put(DEMO, withoutUser, null).getStatusCode());

        assertEquals("demo-admin", externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID).getAvniSystemUser());
    }

    @Test
    public void recordSharingSettingsAreStoredWithTheSharingSecretEncryptedAndNeverReturned() throws Exception {
        assertEquals(HttpStatus.OK, put(DEMO, withRecordSharing(config("test-hip", "test-hip-2")), null).getStatusCode());

        String stored = storedConfig();
        assertFalse(stored, stored.contains("test-sharing-secret"));
        EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals(Map.of("test-hip", "First Centre", "test-hip-2", "Second Centre"), config.getHipNames());
        assertEquals("hiu-1", config.getHiuId());
        assertEquals("clinic-1", config.getClinicId());
        assertEquals("doctor-1", config.getDoctorOid());
        assertEquals("sharing-client-1", config.getSharingClientId());
        assertEquals("test-sharing-secret", ekaCredentialCipher.decrypt(config.getEncryptedSharingClientSecret()));

        ResponseEntity<String> response = get(DEMO);
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals("First Centre", body.at("/hipNames/test-hip").asText());
        assertEquals("hiu-1", body.get("hiuId").asText());
        assertEquals("clinic-1", body.get("clinicId").asText());
        assertEquals("doctor-1", body.get("doctorOid").asText());
        assertEquals("sharing-client-1", body.get("sharingClientId").asText());
        assertTrue(body.get("sharingClientSecretSet").asBoolean());
        assertEquals(config.getWebhookId(), body.get("webhookId").asText());
        assertFalse(response.getBody(), response.getBody().contains("test-sharing-secret"));
        assertFalse(response.getBody(), response.getBody().toLowerCase().contains("encrypted"));
    }

    // The webhook ID is part of the address Eka is given: made once, by the server, and kept.
    @Test
    public void theWebhookIdIsMadeOnTheFirstSaveNeverTakenFromTheRequestAndNeverChanged() throws Exception {
        Map<String, Object> request = config("test-hip");
        request.put("webhookId", "chosen-by-the-caller");
        assertEquals(HttpStatus.OK, put(DEMO, request, null).getStatusCode());
        String webhookId = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID).getWebhookId();
        assertNotNull(webhookId);
        assertTrue(webhookId, webhookId.length() >= 20);
        assertNotEquals("chosen-by-the-caller", webhookId);

        assertEquals(HttpStatus.OK, put(DEMO, request, null).getStatusCode());
        assertEquals(webhookId, externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID).getWebhookId());
    }

    @Test
    public void omittedRecordSharingSettingsAreKept_andABlankOneIsCleared() throws Exception {
        put(DEMO, withRecordSharing(config("test-hip", "test-hip-2")), null);
        EkaSystemConfig first = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);

        assertEquals(HttpStatus.OK, put(DEMO, config("test-hip", "test-hip-2"), null).getStatusCode());
        EkaSystemConfig kept = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals(first.getHipNames(), kept.getHipNames());
        assertEquals("hiu-1", kept.getHiuId());
        assertEquals("clinic-1", kept.getClinicId());
        assertEquals("doctor-1", kept.getDoctorOid());
        assertEquals("sharing-client-1", kept.getSharingClientId());
        assertEquals(first.getEncryptedSharingClientSecret(), kept.getEncryptedSharingClientSecret());

        Map<String, Object> noHiu = config("test-hip");
        noHiu.put("hiuId", "");
        assertEquals(HttpStatus.OK, put(DEMO, noHiu, null).getStatusCode());
        EkaSystemConfig cleared = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertNull(cleared.getHiuId());
        assertEquals("a centre no longer listed keeps no name", Map.of("test-hip", "First Centre"), cleared.getHipNames());

        Map<String, Object> noSharingClient = config("test-hip");
        noSharingClient.put("sharingClientId", "");
        assertEquals(HttpStatus.OK, put(DEMO, noSharingClient, null).getStatusCode());
        EkaSystemConfig noClient = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertNull(noClient.getSharingClientId());
        assertNull("the secret belongs to the client cleared", noClient.getEncryptedSharingClientSecret());
    }

    // The stored sharing secret belongs to the stored sharing client and environment, as the M1 secret does.
    @Test
    public void aSharingClientWithoutItsSecretIs400() {
        Map<String, Object> firstWithoutSecret = withRecordSharing(config("test-hip"));
        firstWithoutSecret.remove("sharingClientSecret");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, firstWithoutSecret, null).getStatusCode());

        assertEquals(HttpStatus.OK, put(DEMO, withRecordSharing(config("test-hip")), null).getStatusCode());
        Map<String, Object> otherClient = config("test-hip");
        otherClient.put("sharingClientId", "sharing-client-2");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherClient, null).getStatusCode());

        Map<String, Object> otherEnvironment = config("test-hip");
        otherEnvironment.put("environment", "production");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherEnvironment, null).getStatusCode());

        Map<String, Object> secretWithoutClient = config("test-hip");
        secretWithoutClient.put("sharingClientId", "");
        secretWithoutClient.put("sharingClientSecret", "test-sharing-secret");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, secretWithoutClient, null).getStatusCode());
    }

    // The M1 client's token is handed to the phone; record sharing must never log in as that client (H1).
    @Test
    public void aSharingClientThatIsTheM1ClientIs400OnTheFirstSave() {
        Map<String, Object> same = withRecordSharing(config("test-hip"));
        same.put("sharingClientId", "client-1");
        ResponseEntity<String> response = put(DEMO, same, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("sharingClientId"));

        Map<String, Object> sameWithSpaces = withRecordSharing(config("test-hip"));
        sameWithSpaces.put("sharingClientId", " client-1 ");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, sameWithSpaces, null).getStatusCode());
        assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.external_system_config where system_name = 'Eka' and organisation_id = ?", Integer.class, DEMO_ID));
    }

    @Test
    public void anUpdateThatMakesTheSharingClientTheM1ClientIs400() throws Exception {
        assertEquals(HttpStatus.OK, put(DEMO, withRecordSharing(config("test-hip")), null).getStatusCode());
        EkaSystemConfig before = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);

        Map<String, Object> sharingBecomesM1 = config("test-hip");
        sharingBecomesM1.put("sharingClientId", "client-1");
        sharingBecomesM1.put("sharingClientSecret", "test-secret-value");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, sharingBecomesM1, null).getStatusCode());

        Map<String, Object> m1BecomesSharing = config("test-hip");
        m1BecomesSharing.put("clientId", "sharing-client-1");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, m1BecomesSharing, null).getStatusCode());

        EkaSystemConfig after = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals("client-1", after.getClientId());
        assertEquals("sharing-client-1", after.getSharingClientId());
        assertEquals(before.getEncryptedSharingClientSecret(), after.getEncryptedSharingClientSecret());
    }

    @Test
    public void aNameForACentreNotListedIs400() {
        Map<String, Object> request = config("test-hip");
        request.put("hipNames", Map.of("test-hip-9", "Elsewhere"));
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, request, null).getStatusCode());

        Map<String, Object> blankName = config("test-hip");
        blankName.put("hipNames", Map.of("test-hip", " "));
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, blankName, null).getStatusCode());
    }

    @Test
    public void missingEnvironmentIs400() {
        Map<String, Object> request = config("test-hip");
        request.remove("environment");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, request, null).getStatusCode());
    }

    // Two first saves at once, a retried request say, must not leave two rows: every later read of the organisation's
    // Eka settings would then fail.
    @Test
    public void concurrentFirstSavesMakeOneRow() throws Exception {
        User superAdmin = userRepository.getDefaultSuperAdmin();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return put(DEMO, config("test-hip"), null).getStatusCode();
            }));
        }
        start.countDown();
        for (Future<HttpStatusCode> result : results) {
            assertEquals(HttpStatus.OK, result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(Integer.valueOf(1), new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.external_system_config where system_name = 'Eka' and organisation_id = ?", Integer.class, DEMO_ID));
    }

    // The Spring Data REST export would hand any of the organisation's users, field users included, the encrypted secrets.
    @Test
    public void ekaRowIsNotExposedOverSpringDataRest() {
        put(DEMO, config("test-hip"), null);
        setUser("demo-admin");

        ResponseEntity<String> collection = template.getForEntity("/externalSystemConfigs", String.class);
        ResponseEntity<String> search = template.getForEntity("/externalSystemConfigs/search/findBySystemName?systemName=Eka", String.class);

        assertNotEquals(String.valueOf(collection.getBody()), HttpStatus.OK, collection.getStatusCode());
        assertNotEquals(String.valueOf(search.getBody()), HttpStatus.OK, search.getStatusCode());
        assertFalse(String.valueOf(collection.getBody()).contains("encryptedClientSecret"));
        assertFalse(String.valueOf(search.getBody()).contains("encryptedClientSecret"));
    }

    @Test
    public void firstSaveWithoutSecretIs400() {
        Map<String, Object> withoutSecret = config("test-hip");
        withoutSecret.remove("clientSecret");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, withoutSecret, null).getStatusCode());
    }

    @Test
    public void hipIdListedByAnotherOrganisationIsRefused() {
        assertEquals(HttpStatus.OK, put(DEMO, config("test-hip"), null).getStatusCode());

        Map<String, Object> clash = config("test-hip-2", "test-hip");
        clash.put("avniSystemUser", "a-demo-admin");
        ResponseEntity<String> response = put(A_DEMO, clash, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("test-hip"));
        assertEquals("the organisation's own health centre IDs are not a clash", HttpStatus.OK, put(DEMO, config("test-hip"), null).getStatusCode());
    }

    @Test
    public void unknownEnvironmentIs400() {
        Map<String, Object> request = config("test-hip");
        request.put("environment", "staging");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, request, null).getStatusCode());
    }

    // An account administrator acting for no organisation reads every organisation's rows, so is never one's system user.
    @Test
    public void anAccountAdministratorAsAvniSystemUserIs400() {
        new JdbcTemplate(dataSource).update("insert into account_admin (name, account_id, admin_id) " +
                "select 'demo-admin', o.account_id, u.id from organisation o, users u where o.id = ? and u.username = 'demo-admin'", DEMO_ID);

        ResponseEntity<String> response = put(DEMO, config("test-hip"), null);

        assertEquals(response.getBody(), HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("account administrator"));
        assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.external_system_config where system_name = 'Eka'", Integer.class));
    }

    @Test
    public void avniSystemUserFromOtherOrganisationIs400() {
        Map<String, Object> otherOrganisationsUser = config("test-hip");
        otherOrganisationsUser.put("avniSystemUser", "a-demo-admin");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherOrganisationsUser, null).getStatusCode());

        Map<String, Object> unknownUser = config("test-hip");
        unknownUser.put("avniSystemUser", "nobody@demo");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, unknownUser, null).getStatusCode());
    }

    private String storedConfig() {
        return new JdbcTemplate(dataSource).queryForObject(
                "select config::text from public.external_system_config where system_name = 'Eka' and organisation_id = ?", String.class, DEMO_ID);
    }

    private static Map<String, Object> config(String... hipIds) {
        Map<String, Object> request = new HashMap<>();
        request.put("environment", "sandbox");
        request.put("clientId", "client-1");
        request.put("clientSecret", "test-secret-value");
        request.put("hipIds", Arrays.asList(hipIds));
        request.put("avniSystemUser", "demo-admin");
        request.put("webhookSigningKey", "test-signing-key");
        return request;
    }

    private static Map<String, Object> withRecordSharing(Map<String, Object> request) {
        Map<String, String> names = new HashMap<>(Map.of("test-hip", "First Centre", "test-hip-2", "Second Centre"));
        names.keySet().retainAll((List<?>) request.get("hipIds"));
        request.put("hipNames", names);
        request.put("hiuId", "hiu-1");
        request.put("clinicId", "clinic-1");
        request.put("doctorOid", "doctor-1");
        request.put("sharingClientId", "sharing-client-1");
        request.put("sharingClientSecret", "test-sharing-secret");
        return request;
    }

    private ResponseEntity<String> put(String organisationUUID, Map<String, Object> body, String organisationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (organisationHeader != null) {
            headers.set("ORGANISATION-UUID", organisationHeader);
        }
        return template.exchange(PATH + organisationUUID, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String organisationUUID) {
        return template.getForEntity(PATH + organisationUUID, String.class);
    }
}
