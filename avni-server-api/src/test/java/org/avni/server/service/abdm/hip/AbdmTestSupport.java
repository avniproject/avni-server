package org.avni.server.service.abdm.hip;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Mocks the M2/M3 tests share.
public final class AbdmTestSupport {
    public static final String HIP_ID = "TANUH";
    public static final String HIU_ID = "TANUH-HIU";

    private AbdmTestSupport() {
    }

    // An organisation's Eka settings with one HIP ID, in the sandbox; overrides replace or add keys.
    public static EkaSystemConfig ekaSettings(JsonObject overrides) {
        JsonObject json = new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.HIP_IDS, List.of(HIP_ID))
                .with(EkaSystemConfig.HIP_NAMES, Map.of(HIP_ID, "TANUH Health Centre"))
                .with(EkaSystemConfig.HIU_ID, HIU_ID)
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-client-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-sharing-secret");
        json.putAll(overrides);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(json);
        return new EkaSystemConfig(row);
    }

    public static AbdmSettings settings() {
        return settings(ekaSettings(new JsonObject()));
    }

    public static AbdmSettings settings(EkaSystemConfig ekaSettings) {
        AbdmSettings settings = mock(AbdmSettings.class);
        when(settings.current()).thenReturn(ekaSettings);
        when(settings.find()).thenReturn(Optional.of(ekaSettings));
        return settings;
    }

    // Runs the work in place, as the organisation.
    @SuppressWarnings("unchecked")
    public static AbdmOrgContext orgContext() {
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisationId()).thenReturn(1L);
        when(orgContext.inReadOnlyTransaction(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        return orgContext;
    }

    public static AbdmRetry noWaitRetry() {
        return new AbdmRetry(2, 0);
    }
}
