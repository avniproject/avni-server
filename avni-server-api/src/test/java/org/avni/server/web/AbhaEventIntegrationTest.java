package org.avni.server.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
import org.avni.server.service.abha.AbhaEventService;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Not @Transactional: each request runs on the server's own connection, and the organisation role is applied when a
 * connection is borrowed, so the fixtures must be committed. tear-down.sql cleans up around each test.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaEventIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Long DEMO_ID = 2L;
    private static final String SUBJECT_TYPE_A = "a1b2c3d4-0000-4000-8000-00000000000a";
    private static final String SUBJECT_TYPE_B = "a1b2c3d4-0000-4000-8000-00000000000b";
    private static final String REGISTRAR = "user-no-access";
    private static final String ALL_PRIVILEGES_USER = "demo-user";
    private static final String ABHA_NUMBER = "12-3456-7856-8912";

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
    private TestDataSetupService testDataSetupService;
    @Autowired
    private AbhaEventService abhaEventService;

    @Before
    public void createSubjectTypesAndPrivileges() {
        setUser("demo-admin");
        SubjectType a = subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(SUBJECT_TYPE_A).setName("ABHA person A").build());
        subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(SUBJECT_TYPE_B).setName("ABHA person B").build());
        Group group = groupRepository.save(new TestGroupBuilder().withMandatoryFieldsForNewEntity().withName("ABHA registrars").build());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(userRepository.findByUsername(REGISTRAR)).build());
        testGroupService.updateGroup(group, Collections.singletonMap(
                new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().setSubjectType(a).build(), PrivilegeType.RegisterSubject));
    }

    @Test
    public void recordsEventWithServerSideHipId() {
        readyFor(REGISTRAR, "HIP-2");
        Map<String, Object> body = event(UUID.randomUUID().toString(), "created");
        body.put("hipId", "HIP-FROM-THE-PHONE");
        body.put("userId", 1);
        body.put("organisationId", 1);

        ResponseEntity<String> response = post(REGISTRAR, body);

        assertEquals(response.getBody(), HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> row = onlyRow();
        assertEquals("HIP-2", row.get("hip_id"));
        assertEquals(userRepository.findByUsername(REGISTRAR).getId().longValue(), ((Number) row.get("user_id")).longValue());
        assertEquals(DEMO_ID.longValue(), ((Number) row.get("organisation_id")).longValue());
        assertEquals(body.get("uuid"), row.get("uuid"));
        assertEquals("subject-1", row.get("subject_uuid"));
        assertEquals("created", row.get("action"));
        assertEquals("aadhaar", row.get("route"));
        assertEquals("12345678568912", row.get("abha_number"));
        assertEquals("a@abdm", row.get("abha_address"));
        assertEquals("oid-1", row.get("eka_oid"));
        assertEquals("txn-1", row.get("eka_txn_id"));
        assertEquals(Instant.parse("2026-10-20T06:30:00Z"), ((Timestamp) row.get("consent_given_at")).toInstant());
        assertEquals(Instant.parse("2026-10-20T06:31:00Z"), ((Timestamp) row.get("occurred_at")).toInstant());
    }

    @Test
    public void sameUuidTwiceStoredOnce() {
        readyFor(REGISTRAR, "HIP-1");
        Map<String, Object> body = event(UUID.randomUUID().toString(), "linked");

        assertEquals(HttpStatus.CREATED, post(REGISTRAR, body).getStatusCode());
        assertEquals(HttpStatus.OK, post(REGISTRAR, body).getStatusCode());

        assertEquals(1, rows().size());
    }

    // The card story's "a record arrives and is kept even when the patient is never saved".
    @Test
    public void eventForUnsyncedSubjectStored() {
        readyFor(REGISTRAR, "HIP-1");
        Map<String, Object> body = event(UUID.randomUUID().toString(), "created");
        body.put("subjectUUID", UUID.randomUUID().toString());

        assertEquals(HttpStatus.CREATED, post(REGISTRAR, body).getStatusCode());

        assertEquals(body.get("subjectUUID"), onlyRow().get("subject_uuid"));
    }

    @Test
    public void removedActionFromPhoneRejected() {
        readyFor(REGISTRAR, "HIP-1");

        assertEquals(HttpStatus.BAD_REQUEST, post(REGISTRAR, event(UUID.randomUUID().toString(), "removed")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post(REGISTRAR, event(UUID.randomUUID().toString(), "deleted")).getStatusCode());
        Map<String, Object> noAction = event(UUID.randomUUID().toString(), "created");
        noAction.remove("action");
        assertEquals(HttpStatus.BAD_REQUEST, post(REGISTRAR, noAction).getStatusCode());
        assertEquals(0, rows().size());
    }

    // The app falls back to "unknown" when it cannot map Eka's method, and treats any 4xx as final.
    @Test
    public void incompleteButAllowedRecordsAreStored() {
        readyFor(REGISTRAR, "HIP-1");
        Map<String, Object> unknownRoute = event(UUID.randomUUID().toString(), "created");
        unknownRoute.put("route", "unknown");
        unknownRoute.put("consentGivenAt", null);
        unknownRoute.remove("abhaAddress");
        unknownRoute.remove("ekaTxnId");
        Map<String, Object> noRoute = event(UUID.randomUUID().toString(), "linked");
        noRoute.remove("route");
        noRoute.remove("occurredAt");
        Map<String, Object> newRoute = event(UUID.randomUUID().toString(), "linked");
        newRoute.put("route", "face_scan");

        assertEquals(HttpStatus.CREATED, post(REGISTRAR, unknownRoute).getStatusCode());
        assertEquals(HttpStatus.CREATED, post(REGISTRAR, noRoute).getStatusCode());
        assertEquals(HttpStatus.CREATED, post(REGISTRAR, newRoute).getStatusCode());

        List<Map<String, Object>> rows = rows();
        assertEquals(3, rows.size());
        for (Map<String, Object> row : rows) {
            assertEquals("unknown", row.get("route"));
            assertNotNull(row.get("occurred_at"));
        }
        assertNull(rows.get(0).get("consent_given_at"));
        assertNull(rows.get(0).get("abha_address"));
    }

    @Test
    public void abhaDisabled403() throws Exception {
        storeEkaSettings();
        setHealthCentre(REGISTRAR, "HIP-1");

        assertRefused(HttpStatus.FORBIDDEN, "ABHA_DISABLED", SUBJECT_TYPE_A);
    }

    @Test
    public void noPrivilege403() throws Exception {
        readyFor(REGISTRAR, "HIP-1");

        assertRefused(HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE", SUBJECT_TYPE_B);
    }

    @Test
    public void noHealthCentre403AndNotConfigured409AndUnknownType400() throws Exception {
        enableAbha();
        clearHealthCentre(REGISTRAR);
        assertRefused(HttpStatus.CONFLICT, "ABHA_NOT_CONFIGURED", SUBJECT_TYPE_A);
        storeEkaSettings();
        assertRefused(HttpStatus.FORBIDDEN, "ABHA_NO_HEALTH_CENTRE", SUBJECT_TYPE_A);
        setHealthCentre(REGISTRAR, "HIP-1");
        assertRefused(HttpStatus.BAD_REQUEST, "ABHA_UNKNOWN_SUBJECT_TYPE", "f00dfeed-0000-4000-8000-000000000000");
    }

    // An event is the audit trail: a refused sender leaves nothing behind.
    @Test
    public void refusedEventLeavesNoRow() throws Exception {
        readyFor(REGISTRAR, "HIP-1");
        assertRefused(HttpStatus.FORBIDDEN, "ABHA_NO_PRIVILEGE", SUBJECT_TYPE_B);
        assertEquals(0, rows().size());
    }

    @Test
    public void nothingFromTheBodyIsLoggedBeyondUuidAndAction() {
        readyFor(REGISTRAR, "HIP-1");
        String uuid = UUID.randomUUID().toString();
        Logger avni = (Logger) LoggerFactory.getLogger("org.avni");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger spring = (Logger) LoggerFactory.getLogger("org.springframework");
        // Hibernate's own DEBUG output prints whole entities, and is not on in staging or live, so it stays at its level.
        List<Logger> loggers = Arrays.asList(avni, spring);
        List<Level> levels = new ArrayList<>();
        boolean additive = avni.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        for (Logger l : loggers) {
            levels.add(l.getLevel());
            l.setLevel(Level.DEBUG);
        }
        root.addAppender(appender);
        avni.addAppender(appender);
        avni.setAdditive(false);
        try {
            assertEquals(HttpStatus.CREATED, post(REGISTRAR, event(uuid, "created")).getStatusCode());
        } finally {
            root.detachAppender(appender);
            avni.detachAppender(appender);
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(levels.get(i));
            }
            avni.setAdditive(additive);
        }
        assertTrue("the event's uuid and action must be logged",
                appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(uuid) && e.getFormattedMessage().contains("created")));
        for (ILoggingEvent event : appender.list) {
            if (event.getLoggerName().startsWith("org.springframework.web.client") || event.getLoggerName().startsWith("org.apache.hc")) {
                continue; // the test's own HTTP client, not the server
            }
            String text = event.getFormattedMessage();
            for (String secret : new String[]{"12345678568912", ABHA_NUMBER, "a@abdm", "oid-1", "txn-1"}) {
                assertFalse(event.getLoggerName() + " logged " + secret + ": " + text, text.contains(secret));
            }
        }
    }

    // Written by the server when a sync removes a subject's ABHA or voids the subject (story 4 calls this).
    @Test
    public void removedRowNamesTheSavingUserAndTheirHealthCentre() {
        setHealthCentre(REGISTRAR, "HIP-2");
        setUser(REGISTRAR);

        abhaEventService.recordRemoved("subject-9");

        Map<String, Object> row = onlyRow();
        assertEquals("removed", row.get("action"));
        assertEquals("unknown", row.get("route"));
        assertEquals("subject-9", row.get("subject_uuid"));
        assertEquals("HIP-2", row.get("hip_id"));
        assertEquals(userRepository.findByUsername(REGISTRAR).getId().longValue(), ((Number) row.get("user_id")).longValue());
        assertEquals(DEMO_ID.longValue(), ((Number) row.get("organisation_id")).longValue());
        assertNotNull(row.get("uuid"));
        assertNotNull(row.get("occurred_at"));
        assertNull(row.get("abha_number"));
    }

    // An upload by an admin with no health centre still leaves the record.
    @Test
    public void removedRowForUserWithNoHealthCentreHasEmptyHipId() {
        clearHealthCentre("demo-admin");
        setUser("demo-admin");

        abhaEventService.recordRemoved("subject-9");

        assertEquals("", onlyRow().get("hip_id"));
    }

    // Per-patient audit data of one organisation: a child organisation must not read its parent's rows, which the
    // reference-table policy would allow. Raw SQL under the organisation's role, so only the policy decides.
    @Test
    public void childOrganisationCannotSeeParentsEvents() {
        TestDataSetupService.TestOrganisationData parent = testDataSetupService.setupOrganisation("abhaparent");
        TestDataSetupService.TestOrganisationData child = testDataSetupService.setupOrganisation("abhachild");
        setUser(userRepository.getDefaultSuperAdmin());
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.update("update public.organisation set parent_organisation_id = ? where id = ?", parent.getOrganisationId(), child.getOrganisationId());
        admin.update("insert into public.abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, route, occurred_at, " +
                        "created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values ('rls-probe', ?, 's', ?, 'H', 'created', 'unknown', now(), ?, ?, now(), now())",
                parent.getOrganisationId(), parent.getUser().getId(), parent.getUser().getId(), parent.getUser().getId());

        setUser(parent.getUser());
        assertEquals(Integer.valueOf(1), countProbe());
        setUser(child.getUser());
        assertEquals("a child organisation must not see its parent's ABHA events", Integer.valueOf(0), countProbe());
    }

    private Integer countProbe() {
        return new JdbcTemplate(dataSource).queryForObject("select count(*) from public.abha_event where uuid = 'rls-probe'", Integer.class);
    }

    private void readyFor(String username, String hipId) {
        enableAbha();
        storeEkaSettings();
        setHealthCentre(username, hipId);
    }

    private Map<String, Object> event(String uuid, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", uuid);
        body.put("subjectUUID", "subject-1");
        body.put("subjectTypeUUID", SUBJECT_TYPE_A);
        body.put("action", action);
        body.put("route", "aadhaar");
        body.put("abhaNumber", ABHA_NUMBER);
        body.put("abhaAddress", "a@abdm");
        body.put("ekaOid", "oid-1");
        body.put("ekaTxnId", "txn-1");
        body.put("consentGivenAt", "2026-10-20T06:30:00Z");
        body.put("occurredAt", "2026-10-20T06:31:00Z");
        return body;
    }

    private ResponseEntity<String> post(String username, Map<String, Object> body) {
        setUser(username);
        return template.postForEntity("/abha/events", body, String.class);
    }

    private void assertRefused(HttpStatus status, String code, String subjectTypeUUID) throws Exception {
        Map<String, Object> body = event(UUID.randomUUID().toString(), "created");
        body.put("subjectTypeUUID", subjectTypeUUID);
        ResponseEntity<String> response = post(REGISTRAR, body);
        assertEquals(response.getBody(), status, response.getStatusCode());
        JsonNode json = mapper.readTree(response.getBody());
        assertEquals(response.getBody(), code, json.path("code").asText());
    }

    private List<Map<String, Object>> rows() {
        return new JdbcTemplate(dataSource).queryForList("select * from public.abha_event order by id");
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = rows();
        assertEquals(1, rows.size());
        return rows.get(0);
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

    private void clearHealthCentre(String username) {
        setUser("demo-admin");
        User user = userRepository.findByUsername(username);
        user.setSettings(new JsonObject());
        userRepository.save(user);
    }

    private void setHealthCentre(String username, String hipId) {
        setUser("demo-admin");
        User user = userRepository.findByUsername(username);
        user.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, hipId));
        userRepository.save(user);
    }
}
