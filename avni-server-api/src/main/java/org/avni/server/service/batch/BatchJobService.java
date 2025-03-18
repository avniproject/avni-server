package org.avni.server.service.batch;

import org.avni.server.dao.AvniJobRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.batch.BatchJobStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BatchJobService {
    private final AvniJobRepository jobRepository;

    @Autowired
    public BatchJobService(AvniJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public String getLastSyncAttributesJobStatus(SubjectType subjectType) {
        BatchJobStatus jobStatus = this.jobRepository.getJobStatus("syncAttributesJob", "subjectTypeId", subjectType.getId().toString());
        return jobStatus.status();
    }

    public Map<String, BatchJobStatus> getCannedAnalyticsJobStatus(Organisation organisation) {
        Map<String, BatchJobStatus> cannedAnalyticsJobStatuses = new HashMap<>();
        cannedAnalyticsJobStatuses.put("Setup", this.jobRepository.getJobStatus("cannedAnalyticsSetupJob", "organisationUUID", organisation.getUuid()));
        cannedAnalyticsJobStatuses.put("CreateQuestionOnly", this.jobRepository.getJobStatus("cannedAnalyticsCreateQuestionOnlyJob", "organisationUUID", organisation.getUuid()));
        cannedAnalyticsJobStatuses.put("TearDown", this.jobRepository.getJobStatus("cannedAnalyticsTearDownJob", "organisationUUID", organisation.getUuid()));
        return cannedAnalyticsJobStatuses;
    }
}
