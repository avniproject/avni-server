package org.avni.server.service.metabase;

import org.avni.server.dao.ImplementationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.domain.metabase.CannedAnalyticsStatus;
import org.avni.server.importer.batch.metabase.CannedAnalyticsLastCompletionStatus;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.batch.BatchJobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CannedAnalyticsStatusService {
    private final OrganisationConfigService organisationConfigService;
    private final BatchJobService batchJobService;
    private final boolean avniReportingMetabaseSelfServiceEnabled;
    private final ImplementationRepository implementationRepository;

    public CannedAnalyticsStatusService(OrganisationConfigService organisationConfigService, BatchJobService batchJobService, @Value("${avni.reporting.metabase.self.service.enabled}") boolean avniReportingMetabaseSelfServiceEnabled, ImplementationRepository implementationRepository) {
        this.organisationConfigService = organisationConfigService;
        this.batchJobService = batchJobService;
        this.avniReportingMetabaseSelfServiceEnabled = avniReportingMetabaseSelfServiceEnabled;
        this.implementationRepository = implementationRepository;
    }

    public CannedAnalyticsStatus getStatus(Organisation organisation) {
        boolean hasETLRun = implementationRepository.hasETLRun(organisation);
        Map<String, BatchJobStatus> cannedAnalyticsJobStatus = batchJobService.getCannedAnalyticsJobStatus(organisation);
        CannedAnalyticsLastCompletionStatus cannedAnalyticsLastCompletionStatus;
        if (!hasETLRun)
            cannedAnalyticsLastCompletionStatus = CannedAnalyticsLastCompletionStatus.EtlNotRun;
        else if (!avniReportingMetabaseSelfServiceEnabled)
            cannedAnalyticsLastCompletionStatus = CannedAnalyticsLastCompletionStatus.NotEnabled;
        else if (organisationConfigService.isMetabaseSetupEnabled(organisation))
            cannedAnalyticsLastCompletionStatus = CannedAnalyticsLastCompletionStatus.Setup;
        else
            cannedAnalyticsLastCompletionStatus = CannedAnalyticsLastCompletionStatus.NotSetup;

        return new CannedAnalyticsStatus(cannedAnalyticsLastCompletionStatus, cannedAnalyticsJobStatus);
    }
}
