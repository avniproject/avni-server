package org.avni.server.web;

import org.avni.server.web.util.Configuration;
import org.avni.server.web.util.ConfigurationResponse;
import org.avni.server.web.util.ReportingSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConfigurationController {
    private final Configuration configuration;

    @Autowired
    public ConfigurationController(Configuration configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/config")
    public ResponseEntity<ConfigurationResponse> getConfig() {
        ConfigurationResponse configurationResponse = new ConfigurationResponse();
        List<ReportingSystem> reportingSystems = configuration.getReportingSystems();
        configurationResponse.setReportingSystems(reportingSystems);
        return ResponseEntity.ok(configurationResponse);
    }
}
