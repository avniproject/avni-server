package org.avni.server.domain.extenalSystem;

import org.avni.server.domain.JsonObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

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

    private EkaSystemConfig configWith(JsonObject json) {
        ExternalSystemConfig externalSystemConfig = new ExternalSystemConfig();
        externalSystemConfig.setSystemName(SystemName.Eka);
        externalSystemConfig.setConfig(json);
        return new EkaSystemConfig(externalSystemConfig);
    }
}
