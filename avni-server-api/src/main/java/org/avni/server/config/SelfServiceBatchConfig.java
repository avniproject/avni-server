package org.avni.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SelfServiceBatchConfig {
    @Value("${avni.reporting.metabase.db.sync.max.timeout.in.minutes}")
    private int avniReportingMetabaseDbSyncMaxTimeoutInMinutes;

    public int getAvniReportingMetabaseDbSyncMaxTimeoutInMinutes() {
        return avniReportingMetabaseDbSyncMaxTimeoutInMinutes;
    }

    public int getTotalTimeoutInMillis() {
        return (this.avniReportingMetabaseDbSyncMaxTimeoutInMinutes + 10) * 60 * 1000;
    }
}
