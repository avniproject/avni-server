package org.avni.server.service.metabase;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.importer.batch.metabase.CannedAnalyticsLastCompletionStatus;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.batch.BatchJobService;
import org.avni.server.domain.metabase.CannedAnalyticsStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CannedAnalyticsStatusService {
    private final OrganisationConfigService organisationConfigService;
    private final BatchJobService batchJobService;

    public CannedAnalyticsStatusService(OrganisationConfigService organisationConfigService, BatchJobService batchJobService) {
        this.organisationConfigService = organisationConfigService;
        this.batchJobService = batchJobService;
    }

    public CannedAnalyticsStatus getStatus(Organisation organisation) {
        CannedAnalyticsStatus cannedAnalyticsStatus;
        if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
            cannedAnalyticsStatus = new CannedAnalyticsStatus(CannedAnalyticsLastCompletionStatus.Setup);
        } else {
            cannedAnalyticsStatus = new CannedAnalyticsStatus(CannedAnalyticsLastCompletionStatus.NotSetup);
        }
        Map<String, BatchJobStatus> cannedAnalyticsJobStatus = batchJobService.getCannedAnalyticsJobStatus(organisation);
        cannedAnalyticsStatus.setJobStatuses(cannedAnalyticsJobStatus);
        return cannedAnalyticsStatus;
    }
}
