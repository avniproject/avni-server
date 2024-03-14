package org.avni.server.web.util;

import java.util.List;
import java.util.stream.Collectors;

public class ConfigurationResponse {
    private List<ReportingSystem> reportingSystems;

    public List<ReportingSystem> getReportingSystems() {
        return reportingSystems;
    }

    public void setReportingSystems(List<ReportingSystem> reportingSystems) {
        List<ReportingSystem> filteredReportingSystems = reportingSystems.stream().filter(reportingSystem -> !(reportingSystem.getName().equals("") || reportingSystem.getUrl().equals("")) ).collect(Collectors.toList());
        this.reportingSystems = filteredReportingSystems;
    }
}
