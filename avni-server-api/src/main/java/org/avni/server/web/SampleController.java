package org.avni.server.web;

import org.avni.server.web.external.RuleServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleController {
    private final RuleServiceClient ruleServiceClient;

    @Autowired
    public SampleController(RuleServiceClient ruleServiceClient) {
        this.ruleServiceClient = ruleServiceClient;
    }

    @RequestMapping("/ping")
    String ping() {
        return "pong";
    }

    @RequestMapping("/hello")
    String hello() {
        return "world";
    }

    @RequestMapping("/ping/rules-server")
    ResponseEntity<String> pingRulesServer() {
        if (ruleServiceClient.isRulesServerUp()) {
            return ResponseEntity.ok("pong");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("rules-server is not reachable");
    }
}
