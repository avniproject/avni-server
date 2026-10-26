package org.avni.server.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRepository;
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
import org.avni.server.service.abha.AbhaHealthCentreService;
import org.avni.server.service.abha.AbhaLinkService;
import org.avni.server.service.abha.AbhaService;
import org.avni.server.service.abha.EkaAuthException;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.avni.server.service.abha.EkaToken;
import org.avni.server.service.abha.EkaTokenService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.TestGroupService;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Not @Transactional: each request runs on the server's own connection, and the organisation role is applied when a
 * connection is borrowed, so the fixtures must be committed. tear-down.sql cleans up around each test.
 *
 * Refusals go through the whole HTTP stack; none of them reaches Eka. Eka's address is fixed by the environment, so a
 * request that passes every check runs the real controller and service, with every real bean except a stand-in
 * EkaTokenService, on the test thread, where the organisation's row filter applies as it does on the server.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaControllerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Long DEMO_ID = 2L;
    private static final String SUBJECT_TYPE_A = "a1b2c3d4-0000-4000-8000-00000000000a";
    private static final String SUBJECT_TYPE_B = "a1b2c3d4-0000-4000-8000-00000000000b";
    // In the All Privileges group.
    private static final String ALL_PRIVILEGES_USER = "demo-user";
    // Both start in the No Access group; the fixtures add one privilege each, on subject type A only.
    private static final String REGISTRAR = "user-no-access";
    private static final String EDITOR = "demouser@demo";
    private static final String ABHA_DIGITS = "12345678568912";

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

    private final EkaTokenService standInEka = mock(EkaTokenService.class);

    @Before
    public void createSubjectTypesAndPrivileges() {
        setUser("demo-admin");
        SubjectType a = subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(SUBJECT_TYPE_A).setName("ABHA person A").build());
        subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(SUBJECT_TYPE_B).setName("ABHA person B").build());
        givePrivilegeOn(a, PrivilegeType.RegisterSubject, "ABHA registrars", REGISTRAR);
        givePrivilegeOn(a, PrivilegeType.EditSubject, "ABHA editors", EDITOR);
    }

    @Test
    public void abhaDisabledIs403() throws Exception {
        storeEkaSettings();
        setHealthCentre(ALL_PRIVILEGES_USER, "HIP-1");

        assertRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_DISABLED");
    }

    @Test
    public void unknownSubjectTypeIs400() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(ALL_PRIVILEGES_USER, "HIP-1");

        assertRefused(ALL_PRIVILEGES_USER, "f00dfeed-0000-4000-8000-000000000000", HttpStatus.BAD_REQUEST, "ABHA_UNKNOWN_SUBJECT_TYPE");
        assertRefused(ALL_PRIVILEGES_USER, null, HttpStatus.BAD_REQUEST, "ABHA_UNKNOWN_SUBJECT_TYPE");
    }

    // Each may register or edit subject type A, and nothing on B.
    @Test
    public void noPrivilegeIs403() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-1");
        setHealthCentre(EDITOR, "HIP-1");

        assertRefused(REGISTRAR, SUBJECT_TYPE_B, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
        assertRefused(EDITOR, SUBJECT_TYPE_B, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
    }

    // A bundle can switch ABHA on in an organisation where Samanvay has not stored Eka settings yet. The app must be able
    // to tell that apart, and a user's health centre cannot be checked without them.
    @Test
    public void notConfiguredIs409() throws Exception {
        enableAbha();
        setHealthCentre(ALL_PRIVILEGES_USER, "HIP-1");

        assertRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.CONFLICT, "ABHA_NOT_CONFIGURED");
    }

    @Test
    public void noHealthCentreIs403() throws Exception {
        enableAbha();
        storeEkaSettings();

        assertRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
    }

    @Test
    public void healthCentreNotListedIs403() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(ALL_PRIVILEGES_USER, "HIP-9");

        assertRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
    }

    @Test
    public void returnsSessionShape() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-2");
        when(standInEka.getAccessToken(DEMO_ID)).thenReturn(new EkaToken("eka-key-for-demo", Instant.parse("2026-10-17T10:30:00.123456Z")));

        MockHttpServletResponse response = getSessionWithStandInEka(REGISTRAR, SUBJECT_TYPE_A);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertEquals("eka-key-for-demo", body.path("accessToken").asText());
        assertEquals("2026-10-17T10:30:00Z", body.path("expiresAt").asText());
        assertEquals("sandbox", body.path("environment").asText());
        assertEquals("HIP-2", body.path("hipId").asText());
        assertEquals(response.getContentAsString(), 4, body.size());
    }

    // A worker who may only edit the subject type still links an ABHA from the edit screen.
    @Test
    public void editOnlyPrivilegeIsAllowed() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(EDITOR, "HIP-1");
        when(standInEka.getAccessToken(DEMO_ID)).thenReturn(new EkaToken("eka-key-for-demo", Instant.parse("2026-10-17T10:30:00Z")));

        MockHttpServletResponse response = getSessionWithStandInEka(EDITOR, SUBJECT_TYPE_A);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        assertEquals("HIP-1", mapper.readTree(response.getContentAsString()).path("hipId").asText());
    }

    // Eka settings stored without a client ID or secret are not set up either.
    @Test
    public void ekaClientNotSetIs409() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-1");
        when(standInEka.getAccessToken(DEMO_ID)).thenThrow(new EkaNotConfiguredException("Eka client id or secret not set"));

        MockHttpServletResponse response = getSessionWithStandInEka(REGISTRAR, SUBJECT_TYPE_A);

        assertEquals(response.getContentAsString(), 409, response.getStatus());
        assertEquals("ABHA_NOT_CONFIGURED", mapper.readTree(response.getContentAsString()).path("code").asText());
    }

    // Eka refusing the organisation's login, or not answering within 10 s, is Eka's fault, not the worker's or the
    // server's: the app can say so and offer to try again.
    @Test
    public void ekaFailureIs502() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-1");
        when(standInEka.getAccessToken(DEMO_ID)).thenThrow(new EkaAuthException("Eka login did not answer within 10 s"));

        MockHttpServletResponse response = getSessionWithStandInEka(REGISTRAR, SUBJECT_TYPE_A);

        assertEquals(response.getContentAsString(), 502, response.getStatus());
        assertEquals("ABHA_EKA_UNAVAILABLE", mapper.readTree(response.getContentAsString()).path("code").asText());
    }

    // Runs as staging and live do, with org.avni's additivity off, so the server's own lines stop at org.avni and never
    // reach ROOT. Captures at both, with everything at DEBUG, Spring's own request handling included.
    @Test
    public void tokenNeverLogged() throws Exception {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-2");
        Long registrarId = userRepository.findByUsername(REGISTRAR).getId();
        when(standInEka.getAccessToken(DEMO_ID)).thenReturn(new EkaToken("eka-key-for-demo", Instant.parse("2026-10-17T10:30:00Z")));

        Logger avni = (Logger) LoggerFactory.getLogger("org.avni");
        boolean avniAdditive = avni.isAdditive();
        List<Logger> loggers = Arrays.asList((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME), avni,
                (Logger) LoggerFactory.getLogger("org.springframework"));
        List<Level> originalLevels = new ArrayList<>();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        for (Logger logger : loggers) {
            originalLevels.add(logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
        loggers.get(0).addAppender(appender);
        avni.addAppender(appender);
        avni.setAdditive(false);
        try {
            MockHttpServletResponse response = getSessionWithStandInEka(REGISTRAR, SUBJECT_TYPE_A);
            assertEquals(response.getContentAsString(), 200, response.getStatus());
            assertTrue(response.getContentAsString().contains("eka-key-for-demo"));
        } finally {
            loggers.get(0).detachAppender(appender);
            avni.detachAppender(appender);
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(originalLevels.get(i));
            }
            avni.setAdditive(avniAdditive);
        }

        assertTrue("the issued-key line must name the user and the organisation",
                appender.list.stream().anyMatch(e -> e.getLoggerName().equals(AbhaService.class.getName()) && e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("user " + registrarId) && e.getFormattedMessage().contains("organisation " + DEMO_ID)));
        assertTrue("Spring's line about writing the response must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().startsWith("org.springframework.web") && e.getFormattedMessage().startsWith("Writing [")));
        for (ILoggingEvent event : appender.list) {
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            assertFalse(event.getLoggerName() + " logged the key: " + text, text.contains("eka-key-for-demo"));
        }
    }

    // The app sends the number as digits. The answer is the UUIDs of the live subjects in the organisation holding it,
    // and nothing else about them; an empty list when nobody does.
    @Test
    public void searchReturnsLiveHoldersOnly() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        String first = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        String second = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, true, false);
        linkedSubject(SUBJECT_TYPE_A, DEMO_ID, "91111122223333", false, false);

        ResponseEntity<String> response = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, ABHA_DIGITS);

        assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals(response.getBody(), 1, body.size());
        List<String> expected = new ArrayList<>(Arrays.asList(first, second));
        Collections.sort(expected);
        assertEquals(expected, uuids(body.path("subjectUUIDs")));

        ResponseEntity<String> nobody = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, "55555666667777");
        assertEquals(nobody.getBody(), HttpStatus.OK, nobody.getStatusCode());
        assertEquals("{\"subjectUUIDs\":[]}", nobody.getBody());
    }

    // OpenCHS (org 1) is demo's parent and a-demo (org 3) its sibling. Neither's patients are demo's.
    @Test
    public void otherOrganisationsSubjectNotReturned() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        String demos = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        linkedSubject(SUBJECT_TYPE_A, 1L, ABHA_DIGITS, false, false);
        linkedSubject(SUBJECT_TYPE_A, 3L, ABHA_DIGITS, false, false);

        ResponseEntity<String> response = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, ABHA_DIGITS);

        assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
        assertEquals(Collections.singletonList(demos), uuids(mapper.readTree(response.getBody()).path("subjectUUIDs")));
    }

    // The privilege is checked on the subject type asked about, so the answer is only subjects of that type.
    @Test
    public void searchAnswersOnlySubjectsOfTheRequestedType() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        String typeA = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        String typeB = linkedSubject(SUBJECT_TYPE_B, DEMO_ID, ABHA_DIGITS, false, false);

        ResponseEntity<String> askedA = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, ABHA_DIGITS);
        ResponseEntity<String> askedB = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_B, ABHA_DIGITS);

        assertEquals(askedA.getBody(), HttpStatus.OK, askedA.getStatusCode());
        assertEquals(Collections.singletonList(typeA), uuids(mapper.readTree(askedA.getBody()).path("subjectUUIDs")));
        assertEquals(askedB.getBody(), HttpStatus.OK, askedB.getStatusCode());
        assertEquals(Collections.singletonList(typeB), uuids(mapper.readTree(askedB.getBody()).path("subjectUUIDs")));
    }

    // Voiding a location's subjects voids them in bulk, by SQL, and leaves their link rows live.
    @Test
    public void subjectVoidedInBulkNotReturned() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, true);

        ResponseEntity<String> response = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, ABHA_DIGITS);

        assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
        assertEquals(Collections.emptyList(), uuids(mapper.readTree(response.getBody()).path("subjectUUIDs")));
    }

    // Each check of /abha/session, in its order, refuses a search with the same status and code.
    @Test
    public void searchRefusedForTheSameReasonsAsSession() throws Exception {
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_DISABLED");
        enableAbha();
        assertSearchRefused(ALL_PRIVILEGES_USER, "f00dfeed-0000-4000-8000-000000000000", HttpStatus.BAD_REQUEST, "ABHA_UNKNOWN_SUBJECT_TYPE");
        assertSearchRefused(ALL_PRIVILEGES_USER, null, HttpStatus.BAD_REQUEST, "ABHA_UNKNOWN_SUBJECT_TYPE");
        assertSearchRefused(REGISTRAR, SUBJECT_TYPE_B, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
        assertSearchRefused(EDITOR, SUBJECT_TYPE_B, HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE");
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.CONFLICT, "ABHA_NOT_CONFIGURED");
        storeEkaSettings();
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
        setHealthCentre(ALL_PRIVILEGES_USER, "HIP-9");
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE");
    }

    @Test
    public void editOnlyPrivilegeMaySearch() throws Exception {
        allowSearch(EDITOR);
        String holder = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);

        ResponseEntity<String> response = search(EDITOR, SUBJECT_TYPE_A, ABHA_DIGITS);

        assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
        assertEquals(Collections.singletonList(holder), uuids(mapper.readTree(response.getBody()).path("subjectUUIDs")));
    }

    // An ABHA made with a mobile number only has an address and no number: the search finds its holder by the address,
    // in any case and with or without the suffix. With neither, there is nothing to check.
    @Test
    public void searchByAddressAloneFindsTheHolder() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        String holder = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        new JdbcTemplate(dataSource).update("update public.abha_link set abha_address = 'Ramesh.Gond@sbx' where individual_id = " +
                "(select id from public.individual where uuid = ?)", holder);

        ResponseEntity<String> response = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, null, "ramesh.gond");

        assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
        assertEquals(Collections.singletonList(holder), uuids(mapper.readTree(response.getBody()).path("subjectUUIDs")));
        ResponseEntity<String> neither = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, null, null);
        assertEquals(neither.getBody(), HttpStatus.BAD_REQUEST, neither.getStatusCode());
    }

    // An ABHA number is 14 digits. Anything else cannot be checked, and an empty answer would read as "nobody holds it".
    @Test
    public void searchNumberNotFourteenDigitsIs400() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);

        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, "1234567856891", HttpStatus.BAD_REQUEST, "ABHA_NUMBER_INVALID");
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, "123456785689123", HttpStatus.BAD_REQUEST, "ABHA_NUMBER_INVALID");
        assertSearchRefused(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, null, HttpStatus.BAD_REQUEST, "ABHA_NUMBER_INVALID");
    }

    // An ABHA number is a health identifier. Captured as staging and live run (org.avni's additivity off), with
    // everything at DEBUG, Spring's request handling included, and written both ways the app or a person may send it.
    @Test
    public void abhaNumberNeverLogged() throws Exception {
        allowSearch(ALL_PRIVILEGES_USER);
        linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);

        Logger avni = (Logger) LoggerFactory.getLogger("org.avni");
        boolean avniAdditive = avni.isAdditive();
        List<Logger> loggers = Arrays.asList((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME), avni,
                (Logger) LoggerFactory.getLogger("org.springframework"));
        List<Level> originalLevels = new ArrayList<>();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        for (Logger logger : loggers) {
            originalLevels.add(logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
        loggers.get(0).addAppender(appender);
        avni.addAppender(appender);
        avni.setAdditive(false);
        try {
            ResponseEntity<String> hyphenated = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, "12-3456-7856-8912");
            ResponseEntity<String> digits = search(ALL_PRIVILEGES_USER, SUBJECT_TYPE_A, ABHA_DIGITS);
            assertEquals(hyphenated.getBody(), HttpStatus.OK, hyphenated.getStatusCode());
            assertEquals(digits.getBody(), HttpStatus.OK, digits.getStatusCode());
        } finally {
            loggers.get(0).detachAppender(appender);
            avni.detachAppender(appender);
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(originalLevels.get(i));
            }
            avni.setAdditive(avniAdditive);
        }

        assertTrue("Spring's line about reading the request body must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().startsWith("org.springframework.web") && e.getFormattedMessage().startsWith("Read ")));
        for (ILoggingEvent event : appender.list) {
            // The test's own HTTP client, which runs in this JVM beside the server, logs the body it sends.
            if (event.getLoggerName().startsWith("org.springframework.web.client") || event.getLoggerName().startsWith("org.apache.hc.client5")) continue;
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            assertFalse(event.getLoggerName() + " logged the ABHA number: " + text, text.contains(ABHA_DIGITS) || text.contains("12-3456-7856-8912"));
        }
    }

    // Spring Data REST exports every repository unless told not to, which would hand any user of the organisation every
    // patient's ABHA number.
    @Test
    public void abhaLinksNotExportedOverSpringDataRest() {
        String holder = linkedSubject(SUBJECT_TYPE_A, DEMO_ID, ABHA_DIGITS, false, false);
        setUser("demo-admin");

        ResponseEntity<String> collection = template.getForEntity("/abhaLinks", String.class);
        ResponseEntity<String> search = template.getForEntity("/abhaLinks/search/findLiveSubjectUUIDs?organisationId=" + DEMO_ID + "&abhaNumber=" + ABHA_DIGITS, String.class);

        assertNotEquals(String.valueOf(collection.getBody()), HttpStatus.OK, collection.getStatusCode());
        assertNotEquals(String.valueOf(search.getBody()), HttpStatus.OK, search.getStatusCode());
        assertFalse(String.valueOf(collection.getBody()).contains(ABHA_DIGITS));
        assertFalse(String.valueOf(search.getBody()).contains(holder));
    }

    private void allowSearch(String username) {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(username, "HIP-1");
    }

    private ResponseEntity<String> search(String username, String subjectTypeUUID, String abhaNumber) {
        return search(username, subjectTypeUUID, abhaNumber, null);
    }

    private ResponseEntity<String> search(String username, String subjectTypeUUID, String abhaNumber, String abhaAddress) {
        setUser(username);
        Map<String, String> request = new HashMap<>();
        request.put("subjectTypeUUID", subjectTypeUUID);
        request.put("abhaNumber", abhaNumber);
        if (abhaAddress != null) request.put("abhaAddress", abhaAddress);
        return template.postForEntity("/abha/subjects/search", request, String.class);
    }

    private void assertSearchRefused(String username, String subjectTypeUUID, HttpStatus status, String code) throws Exception {
        assertSearchRefused(username, subjectTypeUUID, ABHA_DIGITS, status, code);
    }

    private void assertSearchRefused(String username, String subjectTypeUUID, String abhaNumber, HttpStatus status, String code) throws Exception {
        ResponseEntity<String> response = search(username, subjectTypeUUID, abhaNumber);
        assertEquals(response.getBody(), status, response.getStatusCode());
        assertEquals(response.getBody(), code, mapper.readTree(response.getBody()).path("code").asText());
    }

    private static List<String> uuids(JsonNode array) {
        assertTrue(String.valueOf(array), array.isArray());
        List<String> uuids = new ArrayList<>();
        array.forEach(node -> uuids.add(node.asText()));
        return uuids;
    }

    // A subject of the given subject type holding an ABHA number, written as the server's openchs role, which row-level
    // security does not filter, so that any organisation's rows can be made.
    private String linkedSubject(String subjectTypeUUID, Long organisationId, String abhaNumber, boolean linkVoided, boolean subjectVoided) {
        setUser(userRepository.getDefaultSuperAdmin());
        String uuid = UUID.randomUUID().toString();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into public.individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, last_name, " +
                        "gender_id, organisation_id, subject_type_id, observations, is_voided, created_by_id, last_modified_by_id, " +
                        "created_date_time, last_modified_date_time) " +
                        "values (?, 1, 1, current_date, false, 'ABHA', 'Holder', 1, ?, " +
                        "(select id from public.subject_type where uuid = ?), '{}'::jsonb, ?, 1, 1, now(), now())",
                uuid, organisationId, subjectTypeUUID, subjectVoided);
        jdbc.update("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, is_voided, created_by_id, " +
                        "last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, (select id from public.individual where uuid = ?), ?, ?, 1, 1, now(), now())",
                UUID.randomUUID().toString(), organisationId, uuid, abhaNumber, linkVoided);
        return uuid;
    }

    private static String throwableText(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy p = proxy; p != null; p = p.getCause()) {
            text.append(' ').append(p.getMessage());
        }
        return text.toString();
    }

    private MockHttpServletResponse getSessionWithStandInEka(String username, String subjectTypeUUID) throws Exception {
        AbhaService abhaService = new AbhaService(organisationConfigService, subjectTypeRepository, accessControlService,
                externalSystemConfigRepository, abhaHealthCentreService, standInEka, abhaLinkService);
        setUser(username);
        return MockMvcBuilders.standaloneSetup(new AbhaController(abhaService)).build()
                .perform(get("/abha/session").param("subjectTypeUUID", subjectTypeUUID))
                .andReturn().getResponse();
    }

    private void assertRefused(String username, String subjectTypeUUID, HttpStatus status, String code) throws Exception {
        ResponseEntity<String> response = getSession(username, subjectTypeUUID);
        assertEquals(response.getBody(), status, response.getStatusCode());
        JsonNode body = mapper.readTree(response.getBody());
        assertEquals(response.getBody(), code, body.path("code").asText());
    }

    private ResponseEntity<String> getSession(String username, String subjectTypeUUID) {
        setUser(username);
        String query = subjectTypeUUID == null ? "" : "?subjectTypeUUID=" + subjectTypeUUID;
        return template.getForEntity("/abha/session" + query, String.class);
    }

    private void enableAbha() {
        new JdbcTemplate(dataSource).update("update organisation_config set settings = settings || '{\"enableAbha\": true}'::jsonb where organisation_id = ?", DEMO_ID);
    }

    private void storeEkaSettings() {
        setUser("demo-admin");
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setSystemName(SystemName.Eka);
        row.assignUUIDIfRequired();
        row.setOrganisationId(DEMO_ID);
        row.setConfig(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.CLIENT_ID, "client-1")
                .with(EkaSystemConfig.ENCRYPTED_CLIENT_SECRET, ekaCredentialCipher.encrypt("test-secret-value"))
                .with(EkaSystemConfig.HIP_IDS, Arrays.asList("HIP-1", "HIP-2")));
        externalSystemConfigRepository.save(row);
    }

    private void setHealthCentre(String username, String hipId) {
        setUser("demo-admin");
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
}
