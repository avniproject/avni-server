package org.avni.server.service.abdm;

import org.junit.Test;

import java.time.Duration;


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
    private final AbdmSettings settings = org.avni.server.service.abdm.hip.AbdmTestSupport.settings();
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmMessageLog messageLog = mock(AbdmMessageLog.class);
    private final org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData = mock(org.avni.server.service.abdm.hiu.AbdmHiuDataService.class);
    private final org.avni.server.service.abdm.hip.AbdmHipStore hipStore = mock(org.avni.server.service.abdm.hip.AbdmHipStore.class);
    private final org.avni.server.service.abdm.eka.EkaHipService ekaHip = mock(org.avni.server.service.abdm.eka.EkaHipService.class);
    private final org.avni.server.service.abdm.eka.EkaHiuService ekaHiu = mock(org.avni.server.service.abdm.eka.EkaHiuService.class);

    private AbdmHousekeepingJob job() {
        return job(settings);
    }

    private AbdmHousekeepingJob job(AbdmSettings settings) {
        return new AbdmHousekeepingJob(cfg, settings, orgContext, messageLog, hiuData, hipStore, ekaHip, ekaHiu, 30);
    }

    @Test
    public void queuedVisitsUnderEveryHipIdOfTheOrganisationAreLinked_eachPatientOnce() {
        when(cfg.getSystemUser()).thenReturn("abdm-system");
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(orgContext).runAsOrg(any(Runnable.class));
        AbdmSettings twoHips = org.avni.server.service.abdm.hip.AbdmTestSupport.settings(org.avni.server.service.abdm.hip.AbdmTestSupport.ekaSettings(
                new org.avni.server.domain.JsonObject().with(org.avni.server.domain.extenalSystem.EkaSystemConfig.HIP_IDS, java.util.List.of("hip-1", "hip-2"))));
        when(hipStore.subjectsToLink("hip-1", 50)).thenReturn(java.util.List.of("subject-1", "subject-2"));
        when(hipStore.subjectsToLink("hip-2", 50)).thenReturn(java.util.List.of("subject-2", "subject-3"));
        when(ekaHip.linkCareContexts("subject-2", true)).thenThrow(org.avni.server.service.abdm.hip.AbdmRequestException.conflict("Patient has no Eka ID"));

        job(twoHips).linkQueuedVisits();

        verify(ekaHip).linkCareContexts("subject-1", true);
        verify(ekaHip).linkCareContexts("subject-2", true);
        verify(ekaHip).linkCareContexts("subject-3", true);
        verify(hipStore).failQueued("subject-2", "hip-1", "Patient has no Eka ID");
        verify(hipStore).failQueued("subject-2", "hip-2", "Patient has no Eka ID");
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
