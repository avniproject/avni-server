package org.avni.server.domain;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-only-routing filter (avniproject/avni-server#1012, D17): the {@code storageBackends} /
 * {@code storageTargets} keys must never appear in the device-synced org-config serialization.
 */
class OrganisationConfigServerOnlyKeysTest {

    private OrganisationConfig configWithStorageRouting() {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("model", "org-gcs");
        backends.put("default", "avni-s3");

        Map<String, Object> gcsTarget = new LinkedHashMap<>();
        gcsTarget.put("type", "gcs");
        gcsTarget.put("endpoint", "https://storage.googleapis.com");
        gcsTarget.put("bucket", "org-models");
        gcsTarget.put("credentialRef", "org-gcs-creds");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("org-gcs", gcsTarget);

        JsonObject settings = new JsonObject();
        settings.put("languages", new String[]{"en"});
        settings.put(OrganisationConfigSettingKey.storageBackends.name(), backends);
        settings.put(OrganisationConfigSettingKey.storageTargets.name(), targets);

        OrganisationConfig config = new OrganisationConfig();
        config.setSettings(settings);
        return config;
    }

    @Test
    void deviceSerializationStripsServerOnlyKeys() {
        JsonObject serialized = configWithStorageRouting().getSettingsForSerialization();

        assertNull(serialized.get(OrganisationConfigSettingKey.storageBackends.name()),
                "storageBackends must NOT be in the device-synced org config");
        assertNull(serialized.get(OrganisationConfigSettingKey.storageTargets.name()),
                "storageTargets must NOT be in the device-synced org config");
        assertTrue(serialized.containsKey("languages"), "non-server-only keys must still be present");
    }

    @Test
    void withoutServerOnlyKeysDoesNotMutateOriginal() {
        OrganisationConfig config = configWithStorageRouting();
        JsonObject filtered = OrganisationConfig.withoutServerOnlyKeys(config.getSettings());

        assertFalse(filtered.containsKey(OrganisationConfigSettingKey.storageBackends.name()));
        // the original raw settings (server-side) must be untouched
        assertTrue(config.getSettings().containsKey(OrganisationConfigSettingKey.storageBackends.name()),
                "filtering must return a copy and leave the server-side settings intact");
    }

    @Test
    void serverOnlyKeySetContainsBothStorageKeys() {
        assertEquals(2, OrganisationConfig.SERVER_ONLY_SETTING_KEYS.size());
        assertTrue(OrganisationConfig.SERVER_ONLY_SETTING_KEYS.contains(OrganisationConfigSettingKey.storageBackends.name()));
        assertTrue(OrganisationConfig.SERVER_ONLY_SETTING_KEYS.contains(OrganisationConfigSettingKey.storageTargets.name()));
    }

    @Test
    void withoutServerOnlyKeysHandlesNull() {
        assertNull(OrganisationConfig.withoutServerOnlyKeys(null));
    }
}
