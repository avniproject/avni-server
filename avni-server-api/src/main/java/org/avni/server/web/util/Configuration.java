package org.avni.server.web.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "avni")
public class Configuration {
    private List<ReportingSystem> reportingSystems;

    public List<ReportingSystem> getReportingSystems() {
        return reportingSystems;
    }

    public void setReportingSystems(List<ReportingSystem> reportingSystems) {
        this.reportingSystems = reportingSystems;
    }
}