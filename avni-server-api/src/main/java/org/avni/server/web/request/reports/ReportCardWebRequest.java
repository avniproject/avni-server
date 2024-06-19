package org.avni.server.web.request.reports;

public class ReportCardWebRequest extends ReportCardRequest {
    private Long standardReportCardTypeId;

    public Long getStandardReportCardTypeId() {
        return standardReportCardTypeId;
    }

    public void setStandardReportCardTypeId(Long standardReportCardTypeId) {
        this.standardReportCardTypeId = standardReportCardTypeId;
    }
}
