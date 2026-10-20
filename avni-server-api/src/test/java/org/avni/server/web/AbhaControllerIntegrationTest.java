package org.avni.server.web;

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
import org.avni.server.service.abha.AbhaService;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.avni.server.service.abha.EkaToken;
import org.avni.server.service.abha.EkaTokenService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.TestGroupService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
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

    private MockHttpServletResponse getSessionWithStandInEka(String username, String subjectTypeUUID) throws Exception {
        AbhaService abhaService = new AbhaService(organisationConfigService, subjectTypeRepository, accessControlService,
                externalSystemConfigRepository, abhaHealthCentreService, standInEka);
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
