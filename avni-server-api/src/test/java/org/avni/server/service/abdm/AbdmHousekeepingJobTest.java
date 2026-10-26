package org.avni.server.service.abdm;

import org.junit.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: the scheduled ABDM housekeeping (AVNI-13 retention), run as the bridge's organisation.
public class AbdmHousekeepingJobTest {
    private final AbdmConfig cfg = mock(AbdmConfig.class);
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmMessageLog messageLog = mock(AbdmMessageLog.class);
    private final org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData = mock(org.avni.server.service.abdm.hiu.AbdmHiuDataService.class);

    @Test
    public void oldTrafficIsDeletedAsTheOrganisation() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));

        new AbdmHousekeepingJob(cfg, orgContext, messageLog, hiuData, 30).purgeOldTraffic();

        verify(orgContext).runAsOrg(any(Runnable.class));
        verify(messageLog).purgeOlderThan(Duration.ofDays(30));
    }

    @Test
    public void endedConsentsAreExpiredAndTheirRecordsErasedAsTheOrganisation() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));

        new AbdmHousekeepingJob(cfg, orgContext, messageLog, hiuData, 30).expireConsentsAndEraseRecords();

        verify(hiuData).expireAndErase();
    }

    @Test
    public void withNoSystemUserNothingRuns() {
        new AbdmHousekeepingJob(cfg, orgContext, messageLog, hiuData, 30).purgeOldTraffic();

        verify(orgContext, never()).runAsOrg(any(Runnable.class));
        verify(messageLog, never()).purgeOlderThan(any());
    }
}
