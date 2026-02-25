package org.avni.server.web.rules;

import jakarta.transaction.Transactional;
import org.avni.server.service.RuleFailureLogService;
import org.avni.server.web.request.RuleFailureLogRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuleFailureLogController {

    private final RuleFailureLogService ruleFailureLogService;

    @Autowired
    public RuleFailureLogController(RuleFailureLogService ruleFailureLogService) {
        this.ruleFailureLogService = ruleFailureLogService;
    }

    @RequestMapping(value = "/web/ruleFailureLog", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<Void> save(@RequestBody RuleFailureLogRequest request) {
        ruleFailureLogService.saveRuleFailureLog(request);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
