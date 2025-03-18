package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.importer.batch.metabase.CannedAnalyticsLastCompletionStatus;

import java.util.HashMap;
import java.util.Map;

public final class CannedAnalyticsStatus {
    private final CannedAnalyticsLastCompletionStatus status;
    private Map<String, BatchJobStatus> jobStatuses = new HashMap<>();

    public CannedAnalyticsStatus(CannedAnalyticsLastCompletionStatus status, Map<String, BatchJobStatus> jobStatuses) {
        this.status = status;
        this.jobStatuses = jobStatuses;
    }

    public CannedAnalyticsLastCompletionStatus getStatus() {
        return status;
    }

    public Map<String, BatchJobStatus> getJobStatuses() {
        return jobStatuses;
    }

    @JsonIgnore
    public boolean isCreateQuestionAllowed() {
        return status != CannedAnalyticsLastCompletionStatus.NotSetup;
    }
}
