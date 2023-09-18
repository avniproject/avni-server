package org.avni.server.service;

import com.bugsnag.Bugsnag;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.common.ValidationResult;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.observation.PhoneNumber;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.avni.messaging.domain.Constants.DURATION_PATTERN;
import static org.avni.messaging.domain.Constants.PHONE_NUMBER_PATTERN;

@Service("EnhancedValidationService")
@ConditionalOnProperty(value = "avni.enhancedValidation.enabled", havingValue = "true")
public class EnhancedValidationService {
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;
    private final Bugsnag bugsnag;
    private final ConceptRepository conceptRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final Logger logger;
    private final ObjectMapper objectMapper;

    public EnhancedValidationService(FormMappingService formMappingService, OrganisationConfigService organisationConfigService, Bugsnag bugsnag, ConceptRepository conceptRepository, SubjectTypeRepository subjectTypeRepository, IndividualRepository individualRepository, AddressLevelTypeRepository addressLevelTypeRepository) {
        this.formMappingService = formMappingService;
        this.organisationConfigService = organisationConfigService;
        this.bugsnag = bugsnag;
        this.conceptRepository = conceptRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
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
            return handleValidationFailure(errorMessage);
        }

        String allErrors = observationRequests.stream()
            .map(observationRequest -> new SimpleEntry<>(conceptRepository.findByUuid(observationRequest.getConceptUUID()), observationRequest.getValue()))
            .map(this::validate)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));

        if (!allErrors.trim().equals("")) return handleValidationFailure(allErrors);

        return ValidationResult.Success;
    }

    public ValidationResult handleValidationFailure(String errorMessage) {
        logger.error(String.format("ValidationError: %s", errorMessage));
        ValidationException validationException = new ValidationException(errorMessage);
        bugsnag.notify(validationException);
        if (organisationConfigService.isFailOnValidationErrorEnabled()) {
            throw validationException;
        } else {
            return ValidationResult.Failure(errorMessage);
        }
    }

    private List<String> getObservationConceptUuidsFromRequest(List<ObservationRequest> observationRequests) {
        List<String> conceptUuids = observationRequests
            .stream()
            .filter(observationRequest -> observationRequest.getConceptUUID() == null && observationRequest.getConceptName() != null)
            .map(observationRequest -> {
                Concept concept = conceptRepository.findByName(observationRequest.getConceptName());
                if (concept == null) {
                    concept = new Concept();
                    concept.setUuid(observationRequest.getConceptName()); //hack to be able to throw unavailable concept names as error
                } else {
                    observationRequest.setConceptUUID(concept.getUuid()); //make uuid available for further processing to avoid name based searches
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

    private String validate(SimpleEntry<Concept, Object> obsReqAsMap) {
        Concept question = obsReqAsMap.getKey();
        Object value = obsReqAsMap.getValue();
        if (value instanceof Collection<?>) {
            List<String> errorMessages = new ArrayList<>();
            ((Collection<Object>) value).forEach(vl -> {
                String validationResult = validateAnswer(question, vl);
                if (validationResult != null) errorMessages.add(validationResult);
            });
            if (errorMessages.isEmpty()) return null;

            return String.join("\n", errorMessages);
        } else {
            return validateAnswer(question, value);
        }
    }

    private String validateAnswer(Concept question, Object value) {
        if (value == null || (value instanceof String && ((String) value).trim().equals(""))) return null;
        switch (ConceptDataType.valueOf(question.getDataType())) {
            case Coded:
                if (question.getConceptAnswers().stream().noneMatch(ans -> ans.getAnswerConcept().getUuid().equals(value))) {
                    return String.format("Concept answer '%s' not found in Concept '%s'", value, question.getUuid());
                }
                return null;
            case Numeric:
                try {
                    Double.parseDouble(value.toString());
                } catch (NumberFormatException numberFormatException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Text:
                try {
                    String text = (String) value;
                } catch (ClassCastException classCastException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Date:
            case DateTime:
                try {
                    DateTimeUtil.parseNullableDateTime(value.toString());
                } catch (Exception e) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Duration:
                try {
                    String duration = (String)value;
                    if (!duration.matches(DURATION_PATTERN)) {
                        return formatErrorMessage(question, value);
                    }
                } catch (ClassCastException classCastException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Time:
                try {
                    LocalTime.parse(value.toString());
                } catch (DateTimeParseException dateTimeParseException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Subject:
                SubjectType subjectType = subjectTypeRepository.findByUuid(question.getKeyValues().get(KeyType.subjectTypeUUID).getValue().toString());
                if (individualRepository.findByLegacyIdOrUuidAndSubjectType((String)value, subjectType) == null)
                    return formatErrorMessage(question, value);
                return null;
            case Location:
                try {
                    List<String> lowestLevelUuids = (List<String>) question.getKeyValues().get(KeyType.lowestAddressLevelTypeUUIDs).getValue();
                    List<AddressLevelType> lowestLevels = lowestLevelUuids.stream()
                        .map(addressLevelTypeRepository::findByUuid)
                        .collect(Collectors.toList());
                    if (!lowestLevels
                        .stream()
                        .map(AddressLevelType::getAddressLevels)
                        .flatMap(Collection::stream)
                        .map(AddressLevel::getUuid)
                        .collect(Collectors.toList())
                        .contains((String) value)) {
                        return formatErrorMessage(question, value);
                    }
                }
                catch (ClassCastException classCastException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case PhoneNumber:
                try {
                    PhoneNumber phoneNumber = objectMapper.convertValue(value, PhoneNumber.class);
                    if (!phoneNumber.getPhoneNumber().matches(PHONE_NUMBER_PATTERN)) {
                        return formatErrorMessage(question, value);
                    }
                } catch (ClassCastException classCastException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            default:
                return null;
        }
    }

    private String formatErrorMessage(Concept question, Object value) {
        return String.format("Invalid value '%s' for %s concept name: %s, uuid:%s", value, ConceptDataType.valueOf(question.getDataType()), question.getName(), question.getUuid());
    }
}
