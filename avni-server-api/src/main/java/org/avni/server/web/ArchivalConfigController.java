package org.avni.server.web;

import org.avni.server.domain.ArchivalConfig;
import org.avni.server.service.ArchivalConfigService;
import org.avni.server.web.contract.ArchivalConfigContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArchivalConfigController {

    private final ArchivalConfigService archivalConfigService;

    @Autowired
    public ArchivalConfigController(ArchivalConfigService archivalConfigService) {
        this.archivalConfigService = archivalConfigService;
    }

    @PostMapping(value = "/web/archivalConfig")
    public ResponseEntity<ArchivalConfig> createOrUpdateArchivalConfig(@RequestBody ArchivalConfigContract archivalConfig) {
        //TODO: Add privilege check once finalised
        ArchivalConfig savedConfig = archivalConfigService.saveOrUpdate(archivalConfig);
        return new ResponseEntity<>(savedConfig, HttpStatus.CREATED);
    }

    @GetMapping(value = "/web/archivalConfig")
    public ResponseEntity<ArchivalConfigContract> getArchivalConfig() {
        //TODO: Add privilege check once finalised
        ArchivalConfig archivalConfig = archivalConfigService.getArchivalConfig();
        return new ResponseEntity<>(archivalConfig != null ? archivalConfigService.toContract(archivalConfig) : null, HttpStatus.OK);
    }
}
