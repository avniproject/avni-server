package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.web.request.UserBulkUploadContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A user's health centre (settings.abhaHipId) is set from the upload and the admin app. The phone's own profile save
 * and the older bulk save replace the whole settings object, and must not take it away.
 */
@Sql({"/test-data.sql"})
public class UserInfoControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        User user = userRepository.findByUsername("demo-admin");
        user.setSettings(new JsonObject().with(UserSettings.LOCALE, "en").with(UserSettings.ABHA_HIP_ID, "hip-1"));
        userRepository.save(user);
    }

    @After
    public void removeEkaSettings() {
        setUser("demo-admin");
        ExternalSystemConfig row = externalSystemConfigRepository.findBySystemNameAndOrganisationId(SystemName.Eka, 2L);
        if (row != null) externalSystemConfigRepository.delete(row);
    }

    private void demoHasEkaHealthCentres(String... hipIds) {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setSystemName(SystemName.Eka);
        row.assignUUIDIfRequired();
        row.setOrganisationId(2L);
        row.setConfig(new JsonObject().with(EkaSystemConfig.HIP_IDS, Arrays.asList(hipIds)));
        externalSystemConfigRepository.save(row);
    }

    private String savedHipId() {
        return userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.ABHA_HIP_ID);
    }

    @Test
    public void postingSettingsWithoutAbhaHipIdKeepsIt() {
        ResponseEntity<String> response = template.postForEntity(base + "/me", Map.of("settings", Map.of("locale", "hi")), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        User user = userRepository.findByUsername("demo-admin");
        assertEquals("hi", user.getSettings().getString(UserSettings.LOCALE));
        assertEquals("hip-1", savedHipId());
    }

    // The phone posts its whole stored copy of the settings when the user changes, say, the language. A stale copy must
    // not undo an admin's reassignment, and a field user never sets their own health centre.
    @Test
    public void postingAnotherAbhaHipIdKeepsTheStoredOne() {
        ResponseEntity<String> response = template.postForEntity(base + "/me", Map.of("settings", Map.of("locale", "hi", UserSettings.ABHA_HIP_ID, "hip-2")), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
        assertEquals("hip-1", savedHipId());
    }

    @Test
    public void postingAnEmptyAbhaHipIdKeepsTheStoredOne() {
        ResponseEntity<String> response = template.postForEntity(base + "/me", Map.of("settings", Map.of("locale", "hi", UserSettings.ABHA_HIP_ID, "")), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("hip-1", savedHipId());
    }

    @Test
    public void postingAnAbhaHipIdWhenNoneIsStoredSetsNone() {
        User user = userRepository.findByUsername("demo-admin");
        user.setSettings(new JsonObject().with(UserSettings.LOCALE, "en"));
        userRepository.save(user);

        ResponseEntity<String> response = template.postForEntity(base + "/me", Map.of("settings", Map.of("locale", "hi", UserSettings.ABHA_HIP_ID, "hip-2")), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
        assertFalse(userRepository.findByUsername("demo-admin").getSettings().containsKey(UserSettings.ABHA_HIP_ID));
    }

    @Test
    public void bulkSaveWithoutAbhaHipIdKeepsIt() {
        ResponseEntity<String> response = bulkSaveDemoAdminWithSettings(new JsonObject().with(UserSettings.LOCALE, "hi"));

        assertTrue(response.getBody(), response.getStatusCode().is2xxSuccessful());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
        assertEquals("hip-1", savedHipId());
    }

    // The same check as the admin app's save: the health centre must be one of the organisation's.
    @Test
    public void bulkSaveWithAHealthCentreNotTheOrganisationsIsRefused() {
        demoHasEkaHealthCentres("HIP-1", "HIP-2");

        ResponseEntity<String> response = bulkSaveDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "HIP-9"));

        assertEquals(response.getBody(), HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("ABHA Health Facility ID 'HIP-9' is not one of this organisation's health centres"));
        assertEquals("hip-1", savedHipId());
    }

    @Test
    public void bulkSaveWithOneOfTheOrganisationsHealthCentresSetsIt() {
        demoHasEkaHealthCentres("HIP-1", "HIP-2");

        ResponseEntity<String> response = bulkSaveDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "HIP-2"));

        assertTrue(response.getBody(), response.getStatusCode().is2xxSuccessful());
        assertEquals("HIP-2", savedHipId());
    }

    // Checked only when it changes, as in the admin app: a health centre since dropped from Eka's list does not block the save.
    @Test
    public void bulkSaveWithTheStoredHealthCentreUnchangedIsNotChecked() {
        demoHasEkaHealthCentres("HIP-2");

        ResponseEntity<String> response = bulkSaveDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1").with(UserSettings.LOCALE, "hi"));

        assertTrue(response.getBody(), response.getStatusCode().is2xxSuccessful());
        assertEquals("hip-1", savedHipId());
    }

    private ResponseEntity<String> bulkSaveDemoAdminWithSettings(JsonObject settings) {
        User user = userRepository.findByUsername("demo-admin");
        UserBulkUploadContract contract = new UserBulkUploadContract();
        contract.setUuid(user.getUuid());
        contract.setName(user.getUsername());
        contract.setOrganisationId(user.getOrganisationId());
        contract.setCatchmentId(user.getCatchment() == null ? 0 : user.getCatchment().getId());
        contract.setOperatingIndividualScope(OperatingIndividualScope.None.name());
        contract.setEmail("demo-admin@example.com");
        contract.setPhoneNumber("9876543210");
        contract.setSettings(settings);
        return template.postForEntity(base + "/users", new UserBulkUploadContract[]{contract}, String.class);
    }
}
