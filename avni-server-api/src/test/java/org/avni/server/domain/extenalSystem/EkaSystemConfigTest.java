package org.avni.server.domain.extenalSystem;

import org.avni.server.domain.JsonObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EkaSystemConfigTest {

    @Test
    public void sandboxMapsToDevBaseUrl() {
        EkaSystemConfig config = configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "sandbox"));
        assertEquals("https://api.dev.eka.care", config.getBaseUrl());
    }

    @Test
    public void productionMapsToLiveBaseUrl() {
        EkaSystemConfig config = configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "production"));
        assertEquals("https://api.eka.care", config.getBaseUrl());
    }

    @Test(expected = IllegalStateException.class)
    public void unknownEnvironmentThrows() {
        configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "staging")).getBaseUrl();
    }

    @Test
    public void hipIdsReadFromJsonList() {
        EkaSystemConfig config = configWith(new JsonObject().with(EkaSystemConfig.HIP_IDS, Arrays.asList("TANUH", "TANUH-2")));
        assertEquals(Arrays.asList("TANUH", "TANUH-2"), config.getHipIds());
    }

    @Test
    public void missingHipIdsIsEmptyList() {
        EkaSystemConfig config = configWith(new JsonObject());
        assertEquals(Collections.emptyList(), config.getHipIds());
    }

    @Test
    public void theAbhaAddressSuffixFollowsTheEnvironment() {
        assertEquals("@sbx", configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "sandbox")).getAbhaAddressSuffix());
        assertEquals("@abdm", configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "production")).getAbhaAddressSuffix());
    }

    @Test(expected = IllegalStateException.class)
    public void anUnknownEnvironmentHasNoAbhaAddressSuffix() {
        configWith(new JsonObject().with(EkaSystemConfig.ENVIRONMENT, "staging")).getAbhaAddressSuffix();
    }

    @Test
    public void recordSharingSettingsAreRead() {
        EkaSystemConfig config = configWith(new JsonObject()
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2"))
                .with(EkaSystemConfig.HIP_NAMES, Map.of("hip-1", "First Centre"))
                .with(EkaSystemConfig.HIU_ID, "hiu-1")
                .with(EkaSystemConfig.CLINIC_ID, "clinic-1")
                .with(EkaSystemConfig.DOCTOR_OID, "doctor-1")
                .with(EkaSystemConfig.WEBHOOK_ID, "webhook-1")
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1"));

        assertEquals(Map.of("hip-1", "First Centre"), config.getHipNames());
        assertEquals("First Centre", config.getHipName("hip-1"));
        assertEquals("hiu-1", config.getHiuId());
        assertEquals("clinic-1", config.getClinicId());
        assertEquals("doctor-1", config.getDoctorOid());
        assertEquals("webhook-1", config.getWebhookId());
        assertEquals("sharing-1", config.getSharingClientId());
        assertEquals("encrypted-1", config.getEncryptedSharingClientSecret());
        assertTrue(config.isSharingClientConfigured());
    }

    // A centre given no name is shown by its ID.
    @Test
    public void aHipWithoutANameIsNamedByItsId() {
        EkaSystemConfig config = configWith(new JsonObject().with(EkaSystemConfig.HIP_IDS, List.of("hip-1")));

        assertEquals(Collections.emptyMap(), config.getHipNames());
        assertEquals("hip-1", config.getHipName("hip-1"));
    }

    @Test
    public void aSharingClientNeedsBothItsIdAndSecret() {
        assertFalse(configWith(new JsonObject()).isSharingClientConfigured());
        assertFalse(configWith(new JsonObject().with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-1")).isSharingClientConfigured());
        assertFalse(configWith(new JsonObject().with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-1")).isSharingClientConfigured());
    }

    // A HIP ID offered (a recorder's, an event's) is used only when it is the organisation's; else its first.
    @Test
    public void aHipIdIsTheOrganisationsOwnElseItsFirst() {
        EkaSystemConfig config = configWith(new JsonObject().with(EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2")));

        assertEquals("hip-2", config.hipIdOr("hip-2"));
        assertEquals("hip-1", config.hipIdOr("hip-9"));
        assertEquals("hip-1", config.hipIdOr(null));
        assertTrue(config.hasHipId("hip-2"));
        assertFalse(config.hasHipId("hip-9"));
        assertFalse(config.hasHipId(null));
        assertNull(configWith(new JsonObject()).hipIdOr("hip-1"));
    }

    private EkaSystemConfig configWith(JsonObject json) {
        ExternalSystemConfig externalSystemConfig = new ExternalSystemConfig();
        externalSystemConfig.setSystemName(SystemName.Eka);
        externalSystemConfig.setConfig(json);
        return new EkaSystemConfig(externalSystemConfig);
    }
}
