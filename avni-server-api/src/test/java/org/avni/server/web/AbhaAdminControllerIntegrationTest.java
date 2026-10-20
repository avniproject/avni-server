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
        assertEquals(HttpStatus.FORBIDDEN, put(DEMO, config("TANUH"), null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(DEMO).getStatusCode());
    }

    @Test
    public void superAdminActingAsAnOrganisationIs400() {
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, config("TANUH"), DEMO).getStatusCode());
    }

    @Test
    public void organisationOutsideAdminsAccountIs404() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into account (name) select 'eka-test-other-account' where not exists (select 1 from account where name = 'eka-test-other-account')");
        Long accountId = jdbc.queryForObject("select id from account where name = 'eka-test-other-account'", Long.class);
        String otherOrganisation = UUID.randomUUID().toString();
        jdbc.update("insert into organisation (name, db_user, media_directory, uuid, parent_organisation_id, schema_name, category_id, status_id, account_id) " +
                "values ('eka-other', 'eka_other', 'eka-other', ?, 1, 'eka_other', 1, 1, ?)", otherOrganisation, accountId);

        assertEquals(HttpStatus.NOT_FOUND, put(otherOrganisation, config("TANUH"), null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, put(UUID.randomUUID().toString(), config("TANUH"), null).getStatusCode());
    }

    @Test
    public void rowLandsInTargetOrganisation() throws Exception {
        // Loaded as super admin: once acting as one organisation, the other's users are filtered out.
        User demoAdmin = userRepository.findByUsername("demo-admin");
        User aDemoAdmin = userRepository.findByUsername("a-demo-admin");
        assertEquals(HttpStatus.OK, put(DEMO, config("TANUH"), null).getStatusCode());

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
        assertEquals(HttpStatus.OK, put(DEMO, config("TANUH"), null).getStatusCode());

        String stored = storedConfig();
        assertFalse(stored, stored.contains("test-secret-value"));
        assertFalse(stored, stored.contains("test-signing-key"));
        EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals("test-secret-value", ekaCredentialCipher.decrypt(config.getEncryptedClientSecret()));
        assertEquals("test-signing-key", ekaCredentialCipher.decrypt(config.getEncryptedWebhookSigningKey()));
    }

    @Test
    public void getHidesSecrets() throws Exception {
        put(DEMO, config("TANUH", "TANUH-2"), null);

        ResponseEntity<String> response = get(DEMO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals("sandbox", body.get("environment").asText());
        assertEquals("client-1", body.get("clientId").asText());
        assertEquals("[\"TANUH\",\"TANUH-2\"]", body.get("hipIds").toString());
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
        put(DEMO, config("TANUH"), null);
        EkaSystemConfig first = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);

        Map<String, Object> withoutSecrets = config("TANUH");
        withoutSecrets.remove("clientSecret");
        withoutSecrets.remove("webhookSigningKey");
        withoutSecrets.put("clientId", "client-2");
        assertEquals(HttpStatus.OK, put(DEMO, withoutSecrets, null).getStatusCode());
        EkaSystemConfig second = externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID);
        assertEquals("client-2", second.getClientId());
        assertEquals(first.getEncryptedClientSecret(), second.getEncryptedClientSecret());
        assertEquals(first.getEncryptedWebhookSigningKey(), second.getEncryptedWebhookSigningKey());

        Map<String, Object> newSecret = config("TANUH");
        newSecret.put("clientSecret", "rotated-secret-value");
        put(DEMO, newSecret, null);
        assertEquals("rotated-secret-value", ekaCredentialCipher.decrypt(externalSystemConfigRepository.getEkaSystemConfig(DEMO_ID).getEncryptedClientSecret()));

        assertEquals(Integer.valueOf(1), new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.external_system_config where system_name = 'Eka' and organisation_id = ?", Integer.class, DEMO_ID));
    }

    @Test
    public void firstSaveWithoutSecretIs400() {
        Map<String, Object> withoutSecret = config("TANUH");
        withoutSecret.remove("clientSecret");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, withoutSecret, null).getStatusCode());
    }

    @Test
    public void hipIdListedByAnotherOrganisationIsRefused() {
        assertEquals(HttpStatus.OK, put(DEMO, config("TANUH"), null).getStatusCode());

        Map<String, Object> clash = config("TANUH-2", "TANUH");
        clash.put("avniSystemUser", "a-demo-admin");
        ResponseEntity<String> response = put(A_DEMO, clash, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("TANUH"));
        assertEquals("the organisation's own health centre IDs are not a clash", HttpStatus.OK, put(DEMO, config("TANUH"), null).getStatusCode());
    }

    @Test
    public void unknownEnvironmentIs400() {
        Map<String, Object> request = config("TANUH");
        request.put("environment", "staging");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, request, null).getStatusCode());
    }

    @Test
    public void avniSystemUserFromOtherOrganisationIs400() {
        Map<String, Object> otherOrganisationsUser = config("TANUH");
        otherOrganisationsUser.put("avniSystemUser", "a-demo-admin");
        assertEquals(HttpStatus.BAD_REQUEST, put(DEMO, otherOrganisationsUser, null).getStatusCode());

        Map<String, Object> unknownUser = config("TANUH");
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
