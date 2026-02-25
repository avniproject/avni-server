package org.avni.server.service;

import org.avni.server.dao.individualRelationship.RuleFailureLogRepository;
import org.avni.server.domain.RuleFailureLog;
import org.avni.server.web.request.RuleFailureLogRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RuleFailureLogService {

    private final RuleFailureLogRepository ruleFailureLogRepository;

    @Autowired
    public RuleFailureLogService(RuleFailureLogRepository ruleFailureLogRepository) {
        this.ruleFailureLogRepository = ruleFailureLogRepository;
    }

    public RuleFailureLog saveRuleFailureLog(RuleFailureLogRequest request) {
        RuleFailureLog ruleFailureLog = new RuleFailureLog();
        ruleFailureLog.setUuid(request.getUuid() != null ? request.getUuid() : UUID.randomUUID().toString());
        ruleFailureLog.setFormId(request.getFormId());
        ruleFailureLog.setRuleType(request.getRuleType());
        ruleFailureLog.setEntityType(request.getEntityType());
        ruleFailureLog.setEntityId(request.getEntityId());
        ruleFailureLog.setErrorMessage(request.getErrorMessage());
        ruleFailureLog.setStacktrace(request.getStacktrace());
        ruleFailureLog.setSource(request.getSource());
        return ruleFailureLogRepository.save(ruleFailureLog);
    }
}
