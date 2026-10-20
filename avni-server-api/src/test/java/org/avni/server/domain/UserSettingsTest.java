package org.avni.server.domain;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserSettingsTest {
    @Test
    public void keepsWhenIncomingLacksKey() {
        JsonObject existing = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1").with(UserSettings.LOCALE, "en");
        JsonObject incoming = new JsonObject().with(UserSettings.LOCALE, "hi");

        JsonObject merged = UserSettings.mergeKeepingAbhaHipId(existing, incoming);

        assertEquals("hip-1", merged.getString(UserSettings.ABHA_HIP_ID));
        assertEquals("hi", merged.getString(UserSettings.LOCALE));
    }

    @Test
    public void replacesWhenIncomingHasValue() {
        JsonObject existing = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1");
        JsonObject incoming = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-2");

        assertEquals("hip-2", UserSettings.mergeKeepingAbhaHipId(existing, incoming).getString(UserSettings.ABHA_HIP_ID));
    }

    @Test
    public void removesWhenIncomingIsEmptyString() {
        JsonObject existing = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1");
        JsonObject incoming = new JsonObject().with(UserSettings.ABHA_HIP_ID, "").with(UserSettings.LOCALE, "hi");

        JsonObject merged = UserSettings.mergeKeepingAbhaHipId(existing, incoming);

        assertFalse(merged.containsKey(UserSettings.ABHA_HIP_ID));
        assertEquals("hi", merged.getString(UserSettings.LOCALE));
    }

    @Test
    public void nullExistingReturnsIncoming() {
        JsonObject incoming = new JsonObject().with(UserSettings.LOCALE, "hi");

        assertEquals(incoming, UserSettings.mergeKeepingAbhaHipId(null, incoming));
    }

    @Test
    public void nullIncomingIsTreatedAsEmpty() {
        JsonObject existing = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1");

        JsonObject merged = UserSettings.mergeKeepingAbhaHipId(existing, null);

        assertEquals("hip-1", merged.getString(UserSettings.ABHA_HIP_ID));
        assertEquals(1, merged.size());
    }

    @Test
    public void bothNullGivesEmptySettings() {
        assertTrue(UserSettings.mergeKeepingAbhaHipId(null, null).isEmpty());
    }

    @Test
    public void doesNotChangeTheObjectsItIsGiven() {
        JsonObject existing = new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1");
        JsonObject incoming = new JsonObject().with(UserSettings.LOCALE, "hi");

        UserSettings.mergeKeepingAbhaHipId(existing, incoming);

        assertFalse(incoming.containsKey(UserSettings.ABHA_HIP_ID));
        assertEquals("hip-1", existing.getString(UserSettings.ABHA_HIP_ID));
    }
}
