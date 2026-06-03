package org.avni.server.web.request.reports;

import org.avni.server.web.request.CustomCardConfigRequest;

public class ReportCardWebRequest extends ReportCardRequest {
    private Long standardReportCardTypeId;
    private CustomCardConfigRequest customCardConfig;

    public Long getStandardReportCardTypeId() {
        return standardReportCardTypeId;
    }

    public void setStandardReportCardTypeId(Long standardReportCardTypeId) {
        this.standardReportCardTypeId = standardReportCardTypeId;
    }

    public CustomCardConfigRequest getCustomCardConfig() {
        return customCardConfig;
    }

    public void setCustomCardConfig(CustomCardConfigRequest customCardConfig) {
        this.customCardConfig = customCardConfig;
    }
}
