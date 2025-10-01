package org.avni.server.web.util;

import java.util.List;
import java.util.stream.Collectors;

public class ConfigurationResponse {
    private List<ReportingSystem> reportingSystems;
    private CopilotConfig copilotConfig;

    public List<ReportingSystem> getReportingSystems() {
        return reportingSystems;
    }

    public void setReportingSystems(List<ReportingSystem> reportingSystems) {
        List<ReportingSystem> filteredReportingSystems = reportingSystems.stream().filter(reportingSystem -> !(reportingSystem.getName().equals("") || reportingSystem.getUrl().equals("")) ).collect(Collectors.toList());
        this.reportingSystems = filteredReportingSystems;
    }

    public CopilotConfig getCopilotConfig() {
        return copilotConfig;
    }

    public void setCopilotConfig(CopilotConfig copilotConfig) {
        this.copilotConfig = copilotConfig;
    }
}
