package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.avni.server.web.request.UserBulkUploadContract;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * A user's health centre (settings.abhaHipId) is set from the upload and the admin app. The phone's own profile save
 * and the older bulk save replace the whole settings object, and must not take it away.
 */
@Sql({"/test-data.sql"})
public class UserInfoControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        User user = userRepository.findByUsername("demo-admin");
        user.setSettings(new JsonObject().with(UserSettings.LOCALE, "en").with(UserSettings.ABHA_HIP_ID, "hip-1"));
        userRepository.save(user);
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

    @Test
    public void bulkSaveWithoutAbhaHipIdKeepsIt() {
        User user = userRepository.findByUsername("demo-admin");
        UserBulkUploadContract contract = new UserBulkUploadContract();
        contract.setUuid(user.getUuid());
        contract.setName(user.getUsername());
        contract.setOrganisationId(user.getOrganisationId());
        contract.setCatchmentId(user.getCatchment() == null ? 0 : user.getCatchment().getId());
        contract.setOperatingIndividualScope(OperatingIndividualScope.None.name());
        contract.setEmail("demo-admin@example.com");
        contract.setPhoneNumber("9876543210");
        contract.setSettings(new JsonObject().with(UserSettings.LOCALE, "hi"));

        ResponseEntity<String> response = template.postForEntity(base + "/users", new UserBulkUploadContract[]{contract}, String.class);

        assertTrue(response.getBody(), response.getStatusCode().is2xxSuccessful());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
        assertEquals("hip-1", savedHipId());
    }
}
