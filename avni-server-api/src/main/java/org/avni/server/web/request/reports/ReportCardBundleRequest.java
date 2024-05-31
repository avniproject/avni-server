package org.avni.server.web.request.reports;

public class ReportCardBundleRequest extends ReportCardRequest {
    private String standardReportCardType;

    public String getStandardReportCardType() {
        return standardReportCardType;
    }

    public void setStandardReportCardType(String standardReportCardType) {
        this.standardReportCardType = standardReportCardType;
    }
}
