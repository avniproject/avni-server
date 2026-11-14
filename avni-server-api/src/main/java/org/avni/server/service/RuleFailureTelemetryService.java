package org.avni.server.service;

import org.avni.server.dao.RuleFailureTelemetryRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.RuleFailureTelemetry;
import org.avni.server.domain.enums.ruleFailure.AppType;
import org.avni.server.domain.enums.ruleFailure.EntityType;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.rules.response.RuleError;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleFailureTelemetryService {
    private final RuleFailureTelemetryRepository ruleFailureTelemetryRepository;

    public RuleFailureTelemetryService(RuleFailureTelemetryRepository ruleFailureTelemetryRepository) {
        this.ruleFailureTelemetryRepository = ruleFailureTelemetryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEligibilityRuleFailure(Individual individual, String ruleUuid, SourceType sourceType, RuleError error) {
        String errorMessage = error == null ? null : error.getMessage();
        ruleFailureTelemetryRepository.lockFailureUntilTransactionEnds(ruleUuid, individual.getUuid(), errorMessage);
        if (ruleFailureTelemetryRepository.hasOpenFailure(ruleUuid, individual.getUuid(), errorMessage)) return;

        RuleFailureTelemetry ruleFailureTelemetry = new RuleFailureTelemetry();
        ruleFailureTelemetry.setUser(UserContextHolder.getUser());
        ruleFailureTelemetry.setOrganisationId(UserContextHolder.getUserContext().getOrganisation().getId());
        ruleFailureTelemetry.setRuleUuid(ruleUuid);
        ruleFailureTelemetry.setIndividualUuid(individual.getUuid());
        ruleFailureTelemetry.setErrorMessage(errorMessage);
        ruleFailureTelemetry.setStacktrace(error == null ? null : error.getStack());
        ruleFailureTelemetry.setErrorDateTime(DateTime.now());
        ruleFailureTelemetry.setSourceType(sourceType);
        ruleFailureTelemetry.setSourceId(ruleUuid);
        ruleFailureTelemetry.setEntityType(EntityType.Individual);
        ruleFailureTelemetry.setEntityId(individual.getUuid());
        ruleFailureTelemetry.setAppType(AppType.Web);
        ruleFailureTelemetry.setClosed(false);
        ruleFailureTelemetryRepository.save(ruleFailureTelemetry);
    }
}
