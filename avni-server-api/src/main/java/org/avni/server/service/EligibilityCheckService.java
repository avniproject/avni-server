package org.avni.server.service;

import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.Individual;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EligibilityCheckService {
    private static final Logger logger = LoggerFactory.getLogger(EligibilityCheckService.class);
    private final RuleFailureTelemetryService ruleFailureTelemetryService;

    public EligibilityCheckService(RuleFailureTelemetryService ruleFailureTelemetryService) {
        this.ruleFailureTelemetryService = ruleFailureTelemetryService;
    }

    @FunctionalInterface
    public interface EligibilityRuleCall<T> {
        EligibilityRuleResponseEntity execute(List<T> types) throws RuleExecutionException;
    }

    public <T extends CHSBaseEntity> List<T> getEligibleTypes(Individual individual, Collection<T> types, SourceType sourceType, EligibilityRuleCall<T> eligibilityRuleCall) {
        List<T> distinctTypes = new ArrayList<>(types.stream()
                .collect(Collectors.toMap(CHSBaseEntity::getUuid, Function.identity(), (first, second) -> first, LinkedHashMap::new))
                .values());
        if (distinctTypes.isEmpty()) return new ArrayList<>();

        EligibilityRuleResponseEntity eligibilityRuleResponseEntity;
        try {
            eligibilityRuleResponseEntity = eligibilityRuleCall.execute(distinctTypes);
        } catch (RuleExecutionException e) {
            logger.error("{} could not be run for subject {}", sourceType, individual.getUuid());
            return new ArrayList<>();
        }
        if (eligibilityRuleResponseEntity.hasNoResults()) {
            logger.error("{} failed for subject {}, rules server status {}", sourceType, individual.getUuid(), eligibilityRuleResponseEntity.getStatus());
            return new ArrayList<>();
        }

        Map<String, EligibilityRuleEntity> eligibilityRuleEntities = eligibilityRuleResponseEntity.getEligibilityRuleEntities().stream()
                .collect(Collectors.toMap(EligibilityRuleEntity::getTypeUUID, Function.identity(), (first, second) -> first));
        return distinctTypes.stream()
                .filter(type -> {
                    EligibilityRuleEntity eligibilityRuleEntity = eligibilityRuleEntities.get(type.getUuid());
                    if (eligibilityRuleEntity == null) return false;
                    if (eligibilityRuleEntity.hasFailed()) {
                        recordRuleFailure(individual, sourceType, eligibilityRuleEntity);
                        return true;
                    }
                    return eligibilityRuleEntity.isEligible();
                })
                .collect(Collectors.toList());
    }

    private void recordRuleFailure(Individual individual, SourceType sourceType, EligibilityRuleEntity failed) {
        logger.warn("{} rule of {} failed for subject {}; treating it as eligible", sourceType, failed.getTypeUUID(), individual.getUuid());
        try {
            ruleFailureTelemetryService.recordEligibilityRuleFailure(individual, failed.getTypeUUID(), sourceType, failed.getError());
        } catch (RuntimeException e) {
            logger.error("Could not record the failure of the {} rule of {}", sourceType, failed.getTypeUUID(), e);
        }
    }
}
