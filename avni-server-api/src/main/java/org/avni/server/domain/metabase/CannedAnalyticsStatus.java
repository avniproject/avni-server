package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.importer.batch.metabase.CannedAnalyticsLastCompletionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CannedAnalyticsStatus {
    private final CannedAnalyticsLastCompletionStatus status;
    private Map<String, BatchJobStatus> jobStatuses = new HashMap<>();
    private final List<MetabaseResource> resources;
    private final String avniEnvironment;
    private final int timeoutInMillis;

    public CannedAnalyticsStatus(CannedAnalyticsLastCompletionStatus status, Map<String, BatchJobStatus> jobStatuses, List<MetabaseResource> resources, String avniEnvironment, int timeoutInMillis) {
        this.status = status;
        this.jobStatuses = jobStatuses;
        this.resources = resources;
        this.avniEnvironment = avniEnvironment;
        this.timeoutInMillis = timeoutInMillis;
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

    public List<MetabaseResource> getResources() {
        return resources;
    }

    public String getAvniEnvironment() {
        return avniEnvironment;
    }

    public int getTimeoutInMillis() {
        return timeoutInMillis;
    }
}
