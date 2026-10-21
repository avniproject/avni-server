package org.avni.server.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abha.AbhaCardService;
import org.avni.server.service.abha.AbhaHealthCentreService;
import org.avni.server.service.abha.AbhaLinkService;
import org.avni.server.service.abha.AbhaService;
import org.avni.server.service.abha.EkaAuthException;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.avni.server.service.abha.EkaRestClient;
import org.avni.server.service.abha.EkaToken;
import org.avni.server.service.abha.EkaTokenService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.TestGroupService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Not @Transactional: each request runs on the server's own connection, and the organisation role is applied when a
 * connection is borrowed, so the fixtures must be committed. tear-down.sql cleans up around each test.
 *
 * Refusals go through the whole HTTP stack; none of them reaches Eka. Eka's address is fixed by the environment, so a
 * request that passes every check runs the real controller and service, with every real bean except a stand-in
 * EkaTokenService and the real Eka client sent to WireMock, on the test thread inside a read-only transaction as the
 * endpoint's own, where the organisation's row filter applies as it does on the server.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaCardIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Long DEMO_ID = 2L;
    private static final Long A_DEMO_ID = 3L;
    private static final String SUBJECT_TYPE_A = "a1b2c3d4-0000-4000-8000-00000000000a";
    private static final String CARD_PATH = "/abdm/v1/profile/asset/card";
    private static final String SANDBOX_BASE_URL = "https://api.dev.eka.care";
    // In the No Access group, given ViewSubject on A. Catchment Y, which holds Naya Gaon (address level 2).
    private static final String VIEWER = "demouser@demo";
    // In the No Access group, given RegisterSubject on A and nothing else. Catchment X, which holds Naya Gaon too.
    private static final String REGISTRAR = "user-no-access";
    // In the All Privileges group, catchment Y.
    private static final String ALL_PRIVILEGES_USER = "demo-user";
    // In the All Privileges group, catchment X.
    private static final String OTHER_WORKER = "demo-user-2";
    // An organisation admin of a-demo (organisation 3), demo's sibling.
    private static final String OTHER_ORGANISATIONS_USER = "a-demo-admin";
    private static final long IN_CATCHMENT = 2L;
    // GP1, a location of demo's in no catchment of these users.
    private static final long OUT_OF_CATCHMENT = 3L;
    // A PNG signature and then text standing in for the picture. Never a real card.
    private static final byte[] CARD = png("stand-in-card-image");

    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;
    @Autowired
    private EkaCredentialCipher ekaCredentialCipher;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private OrganisationConfigService organisationConfigService;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private AbhaHealthCentreService abhaHealthCentreService;
    @Autowired
    private AbhaLinkService abhaLinkService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private AbhaEventRepository abhaEventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final EkaTokenService standInTokens = mock(EkaTokenService.class);
    private final List<String> baseUrlsAsked = new ArrayList<>();
    private WireMockServer eka;

    @Before
    public void createSubjectTypeAndPrivileges() throws Exception {
        eka = new WireMockServer(options().dynamicPort());
        eka.start();
        setUser("demo-admin");
        SubjectType a = subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(SUBJECT_TYPE_A).setName("ABHA person A").build());
        givePrivilegeOn(a, PrivilegeType.ViewSubject, "ABHA viewers", VIEWER);
        givePrivilegeOn(a, PrivilegeType.RegisterSubject, "ABHA registrars", REGISTRAR);
        when(standInTokens.getAccessToken(DEMO_ID)).thenReturn(new EkaToken("eka-key-for-demo", Instant.now().plus(Duration.ofMinutes(30))));
    }

    @After
    public void stopEka() {
        eka.stop();
    }

    @Test
    public void returnsPngForLinkedSubject() throws Exception {
        readyFor(VIEWER, "HIP-2");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now().minus(Duration.ofDays(3)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        assertEquals("image/png", response.getContentType());
        assertArrayEquals(CARD, response.getContentAsByteArray());
        assertEquals(Collections.singletonList(SANDBOX_BASE_URL), baseUrlsAsked);
        // The user's own health centre, not the one on the record.
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH))
                .withQueryParam("oid", equalTo("oid-1"))
                .withHeader("Authorization", equalTo("Bearer eka-key-for-demo"))
                .withHeader("X-Pt-Id", equalTo("oid-1"))
                .withHeader("X-Hip-Id", equalTo("HIP-2")));
    }

    // The card is a picture of the patient's identity; no cache between Eka and the phone may keep it.
    @Test
    public void responseIsNoStore() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "linked", Instant.now());
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    // A re-linked patient shows the newest ABHA. The server's "removed" record carries no Eka ID and is passed over.
    @Test
    public void newestEventWithAnEkaIdIsUsed() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-old", "created", now.minus(Duration.ofDays(10)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-new", "linked", now.minus(Duration.ofDays(2)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, null, "removed", now.minus(Duration.ofDays(1)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-new")));
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-old")));
    }

    // Straight after a registration that has not synced: the worker who recorded the ABHA sees the card.
    @Test
    public void unsyncedSubjectAllowedForRecorderWithin30Minutes() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(25)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(ALL_PRIVILEGES_USER, unsynced);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        assertArrayEquals(CARD, response.getContentAsByteArray());
    }

    // Anyone else in the organisation who learnt the patient's ID would otherwise see the card.
    @Test
    public void unsyncedSubjectRefusedForOtherUser() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        recordEvent(DEMO_ID, unsynced, OTHER_WORKER, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(5)));

        assertRefused(ALL_PRIVILEGES_USER, unsynced, HttpStatus.FORBIDDEN, "ABHA_NOT_YOURS");
    }

    @Test
    public void unsyncedSubjectRefusedToRecorderAfter30Minutes() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(31)));

        assertRefused(ALL_PRIVILEGES_USER, unsynced, HttpStatus.FORBIDDEN, "ABHA_NOT_YOURS");
    }

    // A patient on the server is decided by the user's right to view them, whoever recorded the ABHA and when.
    @Test
    public void noViewPrivilege403() throws Exception {
        readyFor(REGISTRAR, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, REGISTRAR, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(5)));

        assertRefused(REGISTRAR, subject, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
    }

    // Every privilege, but the patient lives outside the user's catchment, so their phone never holds them.
    @Test
    public void outsideCatchment403() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String subject = subjectOnServer(OUT_OF_CATCHMENT);
        recordEvent(DEMO_ID, subject, ALL_PRIVILEGES_USER, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(5)));

        assertRefused(ALL_PRIVILEGES_USER, subject, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
    }

    // a-demo has ABHA on and the user a health centre, so only the patient can refuse. Demo's patient and demo's records
    // are hidden from a-demo's user, so the patient reads as unsynced and as not theirs.
    @Test
    public void otherOrganisationsUser403() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String synced = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, synced, ALL_PRIVILEGES_USER, "oid-1", "created", Instant.now().minus(Duration.ofMinutes(5)));
        String unsynced = UUID.randomUUID().toString();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, "oid-2", "created", Instant.now().minus(Duration.ofMinutes(5)));
        readyOtherOrganisation();

        assertRefused(OTHER_ORGANISATIONS_USER, synced, HttpStatus.FORBIDDEN, "ABHA_NOT_YOURS");
        assertRefused(OTHER_ORGANISATIONS_USER, unsynced, HttpStatus.FORBIDDEN, "ABHA_NOT_YOURS");
    }

    // "No card yet" carries its own code, so the app can tell it from a server without this endpoint, a bare 404.
    @Test
    public void noEventIs404() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String noRecord = subjectOnServer(IN_CATCHMENT);
        String onlyRemoved = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, onlyRemoved, OTHER_WORKER, null, "removed", Instant.now());

        assertRefused(VIEWER, noRecord, HttpStatus.NOT_FOUND, "ABHA_NO_CARD");
        assertRefused(VIEWER, onlyRemoved, HttpStatus.NOT_FOUND, "ABHA_NO_CARD");
    }

    // Each check of /abha/session that names no subject type refuses the card with the same status and code.
    @Test
    public void refusedForTheSameReasonsAsSession() throws Exception {
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now());

        assertRefused(VIEWER, subject, HttpStatus.FORBIDDEN, "ABHA_DISABLED");
        enableAbha(DEMO_ID);
        assertRefused(VIEWER, subject, HttpStatus.CONFLICT, "ABHA_NOT_CONFIGURED");
        storeEkaSettings("demo-admin", DEMO_ID);
        assertRefused(VIEWER, subject, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
        setHealthCentre(VIEWER, "HIP-9");
        assertRefused(VIEWER, subject, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
    }

    // The sandbox check's failure: Eka refusing the organisation's key is Eka's answer, not "no card".
    @Test
    public void ekaRefusalIs502() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now());
        for (int status : new int[]{401, 403, 404, 500}) {
            eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(status)
                    .withHeader("Content-Type", "application/json").withBody("{\"code\":" + status + ",\"error\":\"no\"}")));

            assertEkaUnavailable(getCardWithEkaOnWireMock(VIEWER, subject));
        }
    }

    @Test
    public void ekaTimeoutFailsCleanly() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now());
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withBody(CARD).withFixedDelay(11000)));

        Instant start = Instant.now();
        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);
        Duration waited = Duration.between(start, Instant.now());

        assertEkaUnavailable(response);
        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(9)) >= 0 && waited.compareTo(Duration.ofSeconds(11)) < 0);
    }

    // The organisation's own login to Eka failing, or its Eka client not set, answers as /abha/session does.
    @Test
    public void ekaLoginFailureIs502AndClientNotSetIs409() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now());

        doThrow(new EkaAuthException("Eka login did not answer within 10 s")).when(standInTokens).getAccessToken(DEMO_ID);
        assertEkaUnavailable(getCardWithEkaOnWireMock(VIEWER, subject));

        doThrow(new EkaNotConfiguredException("Eka client id or secret not set")).when(standInTokens).getAccessToken(DEMO_ID);
        MockHttpServletResponse notSet = getCardWithEkaOnWireMock(VIEWER, subject);
        assertEquals(notSet.getContentAsString(), 409, notSet.getStatus());
        assertEquals("ABHA_NOT_CONFIGURED", mapper.readTree(notSet.getContentAsString()).path("code").asText());

        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)));
    }

    private void assertEkaUnavailable(MockHttpServletResponse response) throws Exception {
        assertEquals(response.getContentAsString(), 502, response.getStatus());
        assertEquals("ABHA_EKA_UNAVAILABLE", mapper.readTree(response.getContentAsString()).path("code").asText());
    }

    private void stubCard() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "image/png").withBody(CARD)));
    }

    MockHttpServletResponse getCardWithEkaOnWireMock(String username, String subjectUUID) {
        AbhaService abhaService = new AbhaService(organisationConfigService, subjectTypeRepository, accessControlService,
                externalSystemConfigRepository, abhaHealthCentreService, standInTokens, abhaLinkService);
        EkaRestClient ekaOnWireMock = new EkaRestClient() {
            @Override
            public byte[] getAbhaCard(String baseUrl, String accessToken, String ekaOid, String hipId) {
                baseUrlsAsked.add(baseUrl);
                return super.getAbhaCard("http://localhost:" + eka.port(), accessToken, ekaOid, hipId);
            }
        };
        AbhaCardService abhaCardService = new AbhaCardService(abhaService, individualRepository, accessControlService, abhaEventRepository, ekaOnWireMock);
        AbhaController controller = new AbhaController(abhaService);
        ReflectionTestUtils.setField(controller, "abhaCardService", abhaCardService);
        setUser(username);
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> {
            try {
                return MockMvcBuilders.standaloneSetup(controller).build()
                        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/abha/card").param("subjectUUID", subjectUUID))
                        .andReturn().getResponse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assertRefused(String username, String subjectUUID, HttpStatus status, String code) throws Exception {
        setUser(username);
        ResponseEntity<String> response = template.getForEntity("/abha/card?subjectUUID=" + subjectUUID, String.class);
        assertEquals(response.getBody(), status, response.getStatusCode());
        assertEquals(response.getBody(), code, mapper.readTree(response.getBody()).path("code").asText());
        assertEquals(response.getBody(), 1, mapper.readTree(response.getBody()).size());
    }

    private void readyFor(String username, String hipId) {
        enableAbha(DEMO_ID);
        storeEkaSettings("demo-admin", DEMO_ID);
        setHealthCentre(username, hipId);
    }

    private void readyOtherOrganisation() {
        setUser(userRepository.getDefaultSuperAdmin());
        new JdbcTemplate(dataSource).update("insert into organisation_config (uuid, organisation_id, settings, version, is_voided, " +
                        "worklist_updation_rule, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, '{\"enableAbha\": true}'::jsonb, 0, false, '', 1, 1, now(), now())",
                UUID.randomUUID().toString(), A_DEMO_ID);
        storeEkaSettings(OTHER_ORGANISATIONS_USER, A_DEMO_ID);
        setHealthCentre(OTHER_ORGANISATIONS_USER, "HIP-1", OTHER_ORGANISATIONS_USER);
    }

    private void enableAbha(Long organisationId) {
        new JdbcTemplate(dataSource).update("update organisation_config set settings = settings || '{\"enableAbha\": true}'::jsonb where organisation_id = ?", organisationId);
    }

    private void storeEkaSettings(String admin, Long organisationId) {
        setUser(admin);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setSystemName(SystemName.Eka);
        row.assignUUIDIfRequired();
        row.setOrganisationId(organisationId);
        row.setConfig(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.CLIENT_ID, "client-1")
                .with(EkaSystemConfig.ENCRYPTED_CLIENT_SECRET, ekaCredentialCipher.encrypt("test-secret-value"))
                .with(EkaSystemConfig.HIP_IDS, Arrays.asList("HIP-1", "HIP-2")));
        externalSystemConfigRepository.save(row);
    }

    private void setHealthCentre(String username, String hipId) {
        setHealthCentre(username, hipId, "demo-admin");
    }

    private void setHealthCentre(String username, String hipId, String admin) {
        setUser(admin);
        User user = userRepository.findByUsername(username);
        user.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, hipId));
        userRepository.save(user);
    }

    private void givePrivilegeOn(SubjectType subjectType, PrivilegeType privilegeType, String groupName, String username) {
        Group group = groupRepository.save(new TestGroupBuilder().withMandatoryFieldsForNewEntity().withName(groupName).build());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(userRepository.findByUsername(username)).build());
        testGroupService.updateGroup(group, Collections.singletonMap(
                new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().setSubjectType(subjectType).build(), privilegeType));
    }

    // A subject of type A in demo, written as the server's openchs role, which row-level security does not filter.
    private String subjectOnServer(long addressLevelId) {
        setUser(userRepository.getDefaultSuperAdmin());
        String uuid = UUID.randomUUID().toString();
        new JdbcTemplate(dataSource).update("insert into public.individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, " +
                        "first_name, last_name, gender_id, organisation_id, subject_type_id, observations, is_voided, created_by_id, " +
                        "last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, 1, current_date, false, 'ABHA', 'Holder', 1, ?, (select id from public.subject_type where uuid = ?), " +
                        "'{}'::jsonb, false, 1, 1, now(), now())",
                uuid, addressLevelId, DEMO_ID, SUBJECT_TYPE_A);
        return uuid;
    }

    private void recordEvent(Long organisationId, String subjectUUID, String username, String ekaOid, String action, Instant occurredAt) {
        setUser(userRepository.getDefaultSuperAdmin());
        new JdbcTemplate(dataSource).update("insert into public.abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, " +
                        "route, eka_oid, occurred_at, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, ?, (select id from public.users where username = ?), 'HIP-1', ?, 'aadhaar', ?, ?, 1, 1, now(), now())",
                UUID.randomUUID().toString(), organisationId, subjectUUID, username, action, ekaOid, Timestamp.from(occurredAt));
    }

    private static byte[] png(String content) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] text = content.getBytes(StandardCharsets.US_ASCII);
        byte[] png = Arrays.copyOf(signature, signature.length + text.length);
        System.arraycopy(text, 0, png, signature.length, text.length);
        return png;
    }
}
