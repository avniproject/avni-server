package org.avni.server.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
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
import java.util.Base64;
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
import static org.junit.Assert.assertFalse;
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

    // The card is the patient's current ABHA: the latest record the server received for them.
    @Test
    public void latestAbhaIsUsed() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-old", "created", now.minus(Duration.ofDays(10)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-new", "linked", now.minus(Duration.ofDays(2)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-new")));
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-old")));
    }

    // An ABHA taken off the patient no longer has a card here.
    @Test
    public void removedAbhaHasNoCard() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", now.minus(Duration.ofDays(2)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, null, "removed", now.minus(Duration.ofDays(1)));

        assertRefused(VIEWER, subject, HttpStatus.NOT_FOUND, "ABHA_NO_CARD");
    }

    @Test
    public void relinkedAfterRemovalShowsTheNewCard() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-old", "created", now.minus(Duration.ofDays(10)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, null, "removed", now.minus(Duration.ofDays(5)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-new", "linked", now.minus(Duration.ofDays(1)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-new")));
    }

    // A later ABHA without an Eka ID must not bring back the card of the ABHA before it.
    @Test
    public void latestAbhaWithoutEkaIdHasNoCard() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-old", "created", now.minus(Duration.ofDays(10)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, null, "linked", now.minus(Duration.ofDays(1)));

        assertRefused(VIEWER, subject, HttpStatus.NOT_FOUND, "ABHA_NO_CARD");
    }

    // The phone's clock can be wrong; the 30 minutes run from when the server received the record.
    @Test
    public void recorderWithSlowPhoneClockGetsCardWithin30MinutesOfReceipt() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        Instant now = Instant.now();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, "oid-1", "created", now.minus(Duration.ofHours(2)), now.minus(Duration.ofMinutes(5)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(ALL_PRIVILEGES_USER, unsynced);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
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

    // An unsynced patient's ID is only a UUID anyone may post a record for, so the recorder is served their own record
    // only, never a later one another worker sent for the same UUID.
    @Test
    public void unsyncedSubjectServesTheRecordersOwnRecordNotAnotherWorkersLaterOne() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        Instant now = Instant.now();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, "oid-mine", "created", now.minus(Duration.ofMinutes(10)));
        recordEvent(DEMO_ID, unsynced, OTHER_WORKER, "oid-theirs", "linked", now.minus(Duration.ofMinutes(2)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(ALL_PRIVILEGES_USER, unsynced);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-mine")));
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-theirs")));
    }

    // The recorder's own latest record decides, with the rule of a synced patient: none without an Eka ID on it.
    @Test
    public void unsyncedSubjectRecordersLatestWithoutEkaIdHasNoCardWhateverAnotherWorkerSent() throws Exception {
        readyFor(ALL_PRIVILEGES_USER, "HIP-1");
        String unsynced = UUID.randomUUID().toString();
        Instant now = Instant.now();
        recordEvent(DEMO_ID, unsynced, ALL_PRIVILEGES_USER, null, "linked", now.minus(Duration.ofMinutes(10)));
        recordEvent(DEMO_ID, unsynced, OTHER_WORKER, "oid-theirs", "linked", now.minus(Duration.ofMinutes(2)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(ALL_PRIVILEGES_USER, unsynced);

        assertEquals(response.getContentAsString(), 404, response.getStatus());
        assertEquals("ABHA_NO_CARD", mapper.readTree(response.getContentAsString()).path("code").asText());
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)));
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

    // A patient's ABHA login at Eka lapses about 30 minutes after it, and Eka then refuses their card with its own 491.
    // That is no outage: the app asks for the ABHA to be verified again rather than for another try.
    @Test
    public void lapsedPatientLoginAtEkaIs409SessionExpired() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-1", "created", Instant.now().minus(Duration.ofHours(1)));
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(491)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":\"SessionExpiredTryAgain\",\"code\":491}")));

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 409, response.getStatus());
        assertEquals("ABHA_SESSION_EXPIRED", mapper.readTree(response.getContentAsString()).path("code").asText());
    }

    // Verifying the ABHA again from its card renews the patient's login at Eka, and the record it leaves names the Eka ID
    // the card is fetched with from then on.
    @Test
    public void verifiedAgainRecordIsTheCurrentAbha() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-old", "created", now.minus(Duration.ofDays(10)));
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-new", "verified", now.minus(Duration.ofMinutes(1)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject);

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-new")));
    }

    // The phone names the ABHA it shows the card for: the record's on the profile, the one just found in a registration
    // or an edit. A later lookup of another ABHA, left unsaved by a worker, must not put that person's card on this
    // patient's profile (27 Oct run: the profile said 2009 and the card was 4533's).
    @Test
    public void theCardIsOfTheAbhaThePhoneNamesNotALaterLookupOfAnother() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-saved", "created", now.minus(Duration.ofDays(2)), "91111122222009");
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-unsaved", "linked", now.minus(Duration.ofMinutes(5)), "91333344444533");
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject, "2009");

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-saved")));
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-unsaved")));
    }

    @Test
    public void theAbhaJustFoundInAnEditHasItsCardBeforeTheSave() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-saved", "created", now.minus(Duration.ofDays(2)), "91111122222009");
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-found", "linked", now.minus(Duration.ofMinutes(1)), "91333344444533");
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject, "4533");

        assertEquals(response.getContentAsString(), 200, response.getStatus());
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-found")));
    }

    @Test
    public void anAbhaThePatientHasNoRecordOfHasNoCard() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-saved", "created", Instant.now().minus(Duration.ofDays(2)), "91111122222009");
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject, "1111");

        assertEquals(response.getContentAsString(), 404, response.getStatus());
        assertEquals("ABHA_NO_CARD", mapper.readTree(response.getContentAsString()).path("code").asText());
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)));
    }

    @Test
    public void anAbhaRemovedSinceHasNoCardThoughThePhoneNamesIt() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String subject = subjectOnServer(IN_CATCHMENT);
        Instant now = Instant.now();
        recordEvent(DEMO_ID, subject, OTHER_WORKER, "oid-saved", "created", now.minus(Duration.ofDays(2)), "91111122222009");
        recordEvent(DEMO_ID, subject, OTHER_WORKER, null, "removed", now.minus(Duration.ofDays(1)));
        stubCard();

        MockHttpServletResponse response = getCardWithEkaOnWireMock(VIEWER, subject, "2009");

        assertEquals(response.getContentAsString(), 404, response.getStatus());
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)));
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

    // The token is the organisation's bearer credential at Eka, the card a picture of the patient's identity, and the
    // Eka ID names the patient at Eka. Runs as staging and live do, with org.avni's additivity off, so the server's own
    // lines stop at org.avni and never reach ROOT; captures at both, everything at DEBUG, Spring's own handling included.
    // A card served, and a card refused by Eka whose error body echoes all three.
    @Test
    public void cardTokenAndEkaIdNeverLogged() throws Exception {
        readyFor(VIEWER, "HIP-1");
        String served = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, served, OTHER_WORKER, "oid-served-1", "created", Instant.now());
        String refused = subjectOnServer(IN_CATCHMENT);
        recordEvent(DEMO_ID, refused, OTHER_WORKER, "oid-refused-2", "created", Instant.now());
        stubCard();
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid-refused-2")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":500,\"error\":\"oid-refused-2 eka-key-for-demo stand-in-card-image\"}")));

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
            MockHttpServletResponse card = getCardWithEkaOnWireMock(VIEWER, served);
            assertEquals(card.getContentAsString(), 200, card.getStatus());
            assertArrayEquals(CARD, card.getContentAsByteArray());
            assertEkaUnavailable(getCardWithEkaOnWireMock(VIEWER, refused));
        } finally {
            loggers.get(0).detachAppender(appender);
            avni.detachAppender(appender);
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(originalLevels.get(i));
            }
            avni.setAdditive(avniAdditive);
        }

        assertTrue("the card-fetched line must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().equals(AbhaCardService.class.getName()) && e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains(served)));
        assertTrue("the card-not-fetched line must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().equals(AbhaCardService.class.getName()) && e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("HTTP 500")));
        assertTrue("Spring's line about writing the response must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().startsWith("org.springframework.web") && e.getFormattedMessage().startsWith("Writing [")));
        String[] secrets = {"eka-key-for-demo", "oid-served-1", "oid-refused-2", "stand-in-card-image", Base64.getEncoder().encodeToString(CARD)};
        for (ILoggingEvent event : appender.list) {
            if (isStubServer(event.getLoggerName()) || isOrmValueTrace(event.getLoggerName())) continue;
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            for (String secret : secrets) {
                assertFalse(event.getLoggerName() + " logged " + secret + ": " + text, text.contains(secret));
            }
        }
    }

    // WireMock is the stand-in for Eka; what it logs about the requests it receives is not the server's logging.
    private static boolean isStubServer(String loggerName) {
        return loggerName.startsWith("com.github.tomakehurst") || loggerName.startsWith("wiremock") || loggerName.startsWith("org.eclipse.jetty");
    }

    // Hibernate at DEBUG prints every value it reads or binds, for every entity: a patient's name as much as an Eka ID.
    // It is off wherever the server runs (logback-spring.xml leaves org.hibernate at Spring Boot's INFO in staging and
    // live), and no line of ABHA code can change it.
    private static boolean isOrmValueTrace(String loggerName) {
        return loggerName.startsWith("org.hibernate.orm.results") || loggerName.startsWith("org.hibernate.orm.jdbc");
    }

    private static String throwableText(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy p = proxy; p != null; p = p.getCause()) {
            text.append(' ').append(p.getMessage());
        }
        return text.toString();
    }

    private void assertEkaUnavailable(MockHttpServletResponse response) throws Exception {
        assertEquals(response.getContentAsString(), 502, response.getStatus());
        assertEquals("ABHA_EKA_UNAVAILABLE", mapper.readTree(response.getContentAsString()).path("code").asText());
    }

    private void stubCard() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "image/png").withBody(CARD)));
    }

    MockHttpServletResponse getCardWithEkaOnWireMock(String username, String subjectUUID) {
        return getCardWithEkaOnWireMock(username, subjectUUID, null);
    }

    MockHttpServletResponse getCardWithEkaOnWireMock(String username, String subjectUUID, String abhaLastFour) {
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
                org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/abha/card").param("subjectUUID", subjectUUID);
                if (abhaLastFour != null) request.header("X-Abha-Last-Four", abhaLastFour);
                return MockMvcBuilders.standaloneSetup(controller).build().perform(request).andReturn().getResponse();
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

    // Received by the server when it happened on the phone.
    private void recordEvent(Long organisationId, String subjectUUID, String username, String ekaOid, String action, Instant occurredAt) {
        recordEvent(organisationId, subjectUUID, username, ekaOid, action, occurredAt, occurredAt);
    }

    private void recordEvent(Long organisationId, String subjectUUID, String username, String ekaOid, String action, Instant occurredAt, String abhaNumber) {
        recordEvent(organisationId, subjectUUID, username, ekaOid, action, occurredAt, occurredAt);
        new JdbcTemplate(dataSource).update("update public.abha_event set abha_number = ? where id = (select max(id) from public.abha_event)", abhaNumber);
    }

    // occurredAt is the phone's time; receivedAt is the server's (created_date_time).
    private void recordEvent(Long organisationId, String subjectUUID, String username, String ekaOid, String action, Instant occurredAt, Instant receivedAt) {
        setUser(userRepository.getDefaultSuperAdmin());
        new JdbcTemplate(dataSource).update("insert into public.abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, " +
                        "route, eka_oid, occurred_at, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, ?, (select id from public.users where username = ?), 'HIP-1', ?, 'aadhaar', ?, ?, 1, 1, ?, ?)",
                UUID.randomUUID().toString(), organisationId, subjectUUID, username, action, ekaOid, Timestamp.from(occurredAt),
                Timestamp.from(receivedAt), Timestamp.from(receivedAt));
    }

    private static byte[] png(String content) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] text = content.getBytes(StandardCharsets.US_ASCII);
        byte[] png = Arrays.copyOf(signature, signature.length + text.length);
        System.arraycopy(text, 0, png, signature.length, text.length);
        return png;
    }
}
