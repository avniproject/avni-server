package org.avni.server.web;

import org.avni.server.web.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigurationController {
    @Autowired
    private Configuration configuration;
    @GetMapping("/Config")
    public ResponseEntity<Configuration> getReportConfig(){
        return ResponseEntity.ok(configuration);
    }
}
