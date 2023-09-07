package org.avni.server.service;

import com.bugsnag.Bugsnag;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.common.ValidationResult;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.validation.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service("EnhancedValidationService")
@ConditionalOnProperty(value = "avni.enhancedValidation.enabled", havingValue = "true")
public class EnhancedValidationService {
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;
    private final Bugsnag bugsnag;
    private final ConceptRepository conceptRepository;

    public EnhancedValidationService(FormMappingService formMappingService, OrganisationConfigService organisationConfigService, Bugsnag bugsnag, ConceptRepository conceptRepository) {
        this.formMappingService = formMappingService;
        this.organisationConfigService = organisationConfigService;
        this.bugsnag = bugsnag;
        this.conceptRepository = conceptRepository;
    }

    public ValidationResult validateObservationsAndDecisionsAgainstFormMapping(List<ObservationRequest> observationRequests, List<Decision> decisions, FormMapping formMapping) {
        LinkedHashMap<String, FormElement> entityConceptMap = formMappingService.getEntityConceptMap(formMapping, true);
        List<String> conceptUuids = getObservationConceptUuidsFromRequest(observationRequests);

        conceptUuids.addAll(getDecisionConceptUuidsFromRequest(decisions));
        List<String> nonMatchingConceptUuids = conceptUuids
            .stream()
            .filter(conceptUuid -> !entityConceptMap.containsKey(conceptUuid))
            .collect(Collectors.toList());

        if (!nonMatchingConceptUuids.isEmpty()) {
            String errorMessage = String.format("Invalid concept uuids/names %s found for Form uuid: %s", String.join(", ", nonMatchingConceptUuids), formMapping.getFormUuid());
            ValidationException validationException = new ValidationException(errorMessage);
            bugsnag.notify(validationException);
            if (organisationConfigService.isFailOnValidationErrorEnabled()) {
                throw validationException;
            } else {
                return ValidationResult.Failure(errorMessage);
            }
        }
        return ValidationResult.Success;
    }

    private List<String> getObservationConceptUuidsFromRequest(List<ObservationRequest> observationRequests) {
        List<String> conceptUuids = observationRequests
            .stream()
            .filter(observationRequest -> observationRequest.getConceptUUID() == null && observationRequest.getConceptName() != null)
            .map(observationRequest -> {
                Concept concept = conceptRepository.findByName(observationRequest.getConceptName());
                if (concept == null) {
                    concept = new Concept();
                    concept.setUuid(observationRequest.getConceptName());
                }
                return concept;
            })
            .map(Concept::getUuid)
            .collect(Collectors.toList());
        conceptUuids.addAll(observationRequests
            .stream().map(ObservationRequest::getConceptUUID)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

        return conceptUuids;
    }

    private List<String> getDecisionConceptUuidsFromRequest(List<Decision> decisions) {
        return decisions != null ? decisions
            .stream()
            .map(decision -> conceptRepository.findByName(decision.getName()))
            .map(Concept::getUuid)
            .collect(Collectors.toList()) : new ArrayList<>();
    }
}
