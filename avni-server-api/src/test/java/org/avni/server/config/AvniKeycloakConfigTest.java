package org.avni.server.config;

import org.junit.Test;

import static org.junit.Assert.*;

public class AvniKeycloakConfigTest {
    @Test
    public void readPrivateKey() {
        AvniKeycloakConfig avniKeycloakConfig = new AvniKeycloakConfig();
        avniKeycloakConfig.postInit();
        assertNotEquals(null, avniKeycloakConfig.getPrivateKey());
    }
}
