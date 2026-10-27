package org.avni.server.service.abdm;

import org.junit.Test;

import java.time.Duration;

import org.avni.server.service.abdm.hip.AbdmAutoLink;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private final org.avni.server.service.abdm.hip.AbdmHipStore hipStore = mock(org.avni.server.service.abdm.hip.AbdmHipStore.class);
    private final org.avni.server.service.abdm.eka.EkaHipService ekaHip = mock(org.avni.server.service.abdm.eka.EkaHipService.class);
    private final org.avni.server.service.abdm.eka.EkaHiuService ekaHiu = mock(org.avni.server.service.abdm.eka.EkaHiuService.class);

    private final AbdmAutoLink autoLink = new AbdmAutoLink(true);

    private AbdmHousekeepingJob job() {
        return new AbdmHousekeepingJob(cfg, orgContext, messageLog, hiuData, hipStore, ekaHip, ekaHiu, autoLink, 30);
    }

    @Test
    public void oldTrafficIsDeletedAsTheOrganisation() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));

        job().purgeOldTraffic();

        verify(orgContext).runAsOrg(any(Runnable.class));
        verify(messageLog).purgeOlderThan(Duration.ofDays(30));
    }

    // HIP_INTI_LINK_505: visits queued on save are linked without anyone pressing a button.
    @Test
    public void queuedVisitsAreLinkedPatientByPatient_andOneThatFailsIsMarkedSoItWaitsBeforeTheNextTry() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        when(cfg.getHipId()).thenReturn("TANUH");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));
        when(hipStore.subjectsToLink("TANUH", 50)).thenReturn(java.util.List.of("subject-1", "subject-2"));
        when(ekaHip.linkCareContexts("subject-1", true)).thenThrow(org.avni.server.service.abdm.hip.AbdmRequestException.conflict("Patient has no Eka ID"));

        job().linkQueuedVisits();

        verify(ekaHip).linkCareContexts("subject-1", true);
        verify(ekaHip).linkCareContexts("subject-2", true);
        verify(hipStore).failQueued("subject-1", "TANUH", "Patient has no Eka ID");
    }

    @Test
    public void parkedConsentUpdatesAreRetriedAsTheOrganisation() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));

        job().expireConsentsAndEraseRecords();

        verify(hiuData).expireAndErase();
        verify(ekaHiu).retryParkedUpdates();
    }

    // A test of linking from the patient's app needs visits nothing has linked first.
    @Test
    public void withLinkingOnSaveSwitchedOffTheJobLinksNothing() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        when(cfg.getHipId()).thenReturn("TANUH");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));
        when(hipStore.subjectsToLink("TANUH", 50)).thenReturn(java.util.List.of("subject-1"));
        autoLink.set(false);

        job().linkQueuedVisits();

        verify(hipStore, never()).subjectsToLink(any(), anyInt());
        verify(ekaHip, never()).linkCareContexts(any(), anyBoolean());
    }

    @Test
    public void endedConsentsAreExpiredAndTheirRecordsErasedAsTheOrganisation() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));

        job().expireConsentsAndEraseRecords();

        verify(hiuData).expireAndErase();
    }

    @Test
    public void withNoSystemUserNothingRuns() {
        job().purgeOldTraffic();

        verify(orgContext, never()).runAsOrg(any(Runnable.class));
        verify(messageLog, never()).purgeOlderThan(any());
    }
}
