package org.avni.server.web.request;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrganisationConfigRequestTest {

    @Test
    public void exportedSettingsOmitServerOnlyStorageKeys() {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("model", "org-gcs");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("org-gcs", Collections.singletonMap("bucket", "org-models"));

        JsonObject settings = new JsonObject();
        settings.put("languages", new String[]{"en"});
        settings.put(OrganisationConfigSettingKey.storageBackends.name(), backends);
        settings.put(OrganisationConfigSettingKey.storageTargets.name(), targets);

        OrganisationConfig config = new OrganisationConfig();
        config.setUuid("config-uuid");
        config.setSettings(settings);

        OrganisationConfigRequest request = OrganisationConfigRequest.fromOrganisationConfig(config);
        JsonObject exported = request.getSettings();

        assertFalse("storageBackends must be absent from the exported organisationConfig.json",
                exported.containsKey(OrganisationConfigSettingKey.storageBackends.name()));
        assertFalse("storageTargets must be absent from the exported organisationConfig.json",
                exported.containsKey(OrganisationConfigSettingKey.storageTargets.name()));
        assertTrue("non-server-only keys must still be exported", exported.containsKey("languages"));

        // the in-memory entity's settings must be untouched (filter returns a copy)
        assertTrue(config.getSettings().containsKey(OrganisationConfigSettingKey.storageBackends.name()));
    }
}
