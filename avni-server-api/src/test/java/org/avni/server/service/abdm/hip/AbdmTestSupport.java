package org.avni.server.service.abdm.hip;

import org.avni.server.service.abdm.AbdmAsync;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmOrgContext;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: mocks the M2/M3 tests share.
public final class AbdmTestSupport {
    public static final String HIP_ID = "TANUH";
    public static final String HIU_ID = "TANUH-HIU";

    private AbdmTestSupport() {
    }

    public static AbdmConfig config() {
        AbdmConfig cfg = mock(AbdmConfig.class);
        when(cfg.getHipId()).thenReturn(HIP_ID);
        when(cfg.getHipName()).thenReturn("TANUH Health Centre");
        when(cfg.getHiuId()).thenReturn(HIU_ID);
        when(cfg.getAbhaAddressSuffix()).thenReturn("@sbx");
        return cfg;
    }

    // Runs the work in place, as the organisation.
    @SuppressWarnings("unchecked")
    public static AbdmOrgContext orgContext() {
        AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
        when(orgContext.organisationId()).thenReturn(1L);
        when(orgContext.inReadOnlyTransaction(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        return orgContext;
    }

    // Runs submitted work at once, on the test thread.
    public static AbdmAsync inlineAsync() {
        AbdmAsync async = mock(AbdmAsync.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(async).submit(anyString(), any(Runnable.class));
        return async;
    }

    public static AbdmRetry noWaitRetry() {
        return new AbdmRetry(2, 0);
    }
}
