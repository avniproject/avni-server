package org.avni.server.web.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.web.OrganisationConfigController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class Configuration {
    @Value("${avni.reportingSystems}")
    private String report;
    private List<ReportingSystem> reportingSystems;

    @Autowired
    private ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(OrganisationConfigController.class);

    public void setReport(String report) {
        System.out.println(report);
        this.report = report;
    }

    public List<ReportingSystem> getReportingSystems() {
        return reportingSystems;
    }

    public void setReportingSystems(List<ReportingSystem> reportingSystems) {
        this.reportingSystems = reportingSystems;
    }

    @PostConstruct
    public void addReportingSystems() {
        try {
            logger.info(String.format("reporting system getting from environemt %s", report));
            reportingSystems = objectMapper.readValue(report, objectMapper.getTypeFactory().constructCollectionType(List.class, ReportingSystem.class));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}
