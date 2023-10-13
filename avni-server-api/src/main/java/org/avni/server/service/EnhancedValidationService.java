package org.avni.server.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import com.amazonaws.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.common.EnhancedValidationDTO;
import org.avni.server.common.ValidationResult;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.observation.PhoneNumber;
import org.avni.server.util.BugsnagReporter;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.avni.messaging.domain.Constants.DURATION_PATTERN;
import static org.avni.messaging.domain.Constants.PHONE_NUMBER_PATTERN;

@Service("EnhancedValidationService")
@ConditionalOnProperty(value = "avni.enhancedValidation.enabled", havingValue = "true")
public class EnhancedValidationService {
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;
    private final BugsnagReporter bugsnagReporter;
    private final ConceptRepository conceptRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final Logger logger;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    public EnhancedValidationService(FormMappingService formMappingService, OrganisationConfigService organisationConfigService, BugsnagReporter bugsnagReporter, ConceptRepository conceptRepository, SubjectTypeRepository subjectTypeRepository, IndividualRepository individualRepository, AddressLevelTypeRepository addressLevelTypeRepository, S3Service s3Service) {
        this.formMappingService = formMappingService;
        this.organisationConfigService = organisationConfigService;
        this.bugsnagReporter = bugsnagReporter;
        this.conceptRepository = conceptRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.s3Service = s3Service;
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public ValidationResult validateObservationsAndDecisionsAgainstFormMapping(List<ObservationRequest> observationRequests, List<Decision> decisions, FormMapping formMapping) {
        LinkedHashMap<String, FormElement> entityConceptMap = formMappingService.getEntityConceptMap(formMapping, true);

        String errorMessage = checkForInvalidConceptUUIDAndNames(observationRequests, decisions, formMapping, entityConceptMap);
        if (StringUtils.hasText(errorMessage)) return handleValidationFailure(errorMessage);

        errorMessage = validateConceptValuesAreOfRequiredType(observationRequests, entityConceptMap);
        if (StringUtils.hasText(errorMessage)) return handleValidationFailure(errorMessage);

        return ValidationResult.Success;
    }

    private String checkForInvalidConceptUUIDAndNames(List<ObservationRequest> observationRequests, List<Decision> decisions, FormMapping formMapping, LinkedHashMap<String, FormElement> entityConceptMap) {
        List<String> conceptUuids = getObservationConceptUuidsFromRequest(observationRequests);

        conceptUuids.addAll(getDecisionConceptUuidsFromRequest(decisions));
        List<String> nonMatchingConceptUuids = conceptUuids
            .stream()
            .filter(conceptUuid -> !entityConceptMap.containsKey(conceptUuid))
            .collect(Collectors.toList());

        if (!nonMatchingConceptUuids.isEmpty()) {
            return String.format("Invalid concept uuids/names %s found for Form uuid/name: %s/%s", String.join(", ", nonMatchingConceptUuids), formMapping.getFormUuid(), formMapping.getFormName());
        }
        return null;
    }

    private String validateConceptValuesAreOfRequiredType(List<ObservationRequest> observationRequests, LinkedHashMap<String, FormElement> entityConceptMap) {
        return observationRequests.stream()
                .map(observationRequest -> new EnhancedValidationDTO(conceptRepository.findByUuid(observationRequest.getConceptUUID()), entityConceptMap.get(observationRequest.getConceptUUID()), observationRequest.getValue()))
                .map(enhancedValidationDTO -> validate(enhancedValidationDTO, entityConceptMap))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    public ValidationResult handleValidationFailure(String errorMessage) {
        ValidationException validationException = new ValidationException(errorMessage);
        bugsnagReporter.logAndReportToBugsnag(validationException);
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

    private String validate(EnhancedValidationDTO enhancedValidationDTO, LinkedHashMap<String, FormElement> entityConceptMap) {
        Object value = enhancedValidationDTO.getValue();
        if (enhancedValidationDTO.getConcept().isQuestionGroup()) {
            return validateQuestionGroupConcept(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(),
                    value, entityConceptMap);
        } else if (value instanceof Collection<?>) {
            List<String> errorMessages = new ArrayList<>();
            ((Collection<Object>) value).forEach(vl -> {
                String validationResult = validateAnswer(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(), vl);
                if (validationResult != null) errorMessages.add(validationResult);
            });
            if (errorMessages.isEmpty()) return null;

            return String.join("\n", errorMessages);
        } else {
            return validateAnswer(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(), value);
        }
    }

    private String validateAnswer(Concept question, FormElement formElement, Object value) {
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
                    if (formElement.getValidFormat() != null) {
                        if (!text.matches(formElement.getValidFormat().getRegex())) {
                            return formatErrorMessage(question, value);
                        }
                    }
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
                        .contains(value)) {
                        return formatErrorMessage(question, value);
                    }
                } catch (ClassCastException classCastException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case PhoneNumber:
                try {
                    PhoneNumber phoneNumber = objectMapper.convertValue(value, PhoneNumber.class);
                    if (!phoneNumber.getPhoneNumber().matches(PHONE_NUMBER_PATTERN)) {
                        return formatErrorMessage(question, value);
                    }
                } catch (ClassCastException | IllegalArgumentException e) {
                    return formatErrorMessage(question, value);
                }
                return null;
            case Image:
                try {
                    URL dummyUrl = s3Service.generateMediaUploadUrl("dummy.jpg", HttpMethod.PUT);
                    URL imageUrl = new URL(value.toString());
                    if (!Objects.equals(dummyUrl.getProtocol(), imageUrl.getProtocol()) || !Objects.equals(dummyUrl.getHost(), imageUrl.getHost())) {
                        return formatErrorMessage(question, value);
                    }
                } catch (MalformedURLException malformedURLException) {
                    return formatErrorMessage(question, value);
                }
                return null;
            default:
                return null;
        }
    }

    public String validateQuestionGroupConcept(Concept question, FormElement formElement, Object qGroupValue,
                                               LinkedHashMap<String, FormElement> entityConceptMap) {
        if(qGroupValue == null) {
            return String.format("Null value specified for question group concept name: %s, uuid:%s", question.getName(), question.getUuid());
        } else if(formElement.isRepeatable() && !(qGroupValue instanceof Collection<?>)) {
            return String.format("Non-repeatable qGroupValue specified for Repeatable question group concept name: %s, uuid:%s", question.getName(), question.getUuid());
        } else if(!formElement.isRepeatable() && qGroupValue instanceof Collection<?>) {
            return String.format("Repeatable qGroupValue specified for Non-Repeatable question group concept name: %s, uuid:%s", question.getName(), question.getUuid());
        }
        return splitQuestionGroupValueIfRequiredAndThenValidate(qGroupValue, entityConceptMap);
    }

    private String splitQuestionGroupValueIfRequiredAndThenValidate(Object qGroupValue, LinkedHashMap<String, FormElement> entityConceptMap) {
        if (qGroupValue instanceof Collection<?>) {
            List<String> errorMessages = new ArrayList<>();
            ((Collection<Object>) qGroupValue).forEach(qGroupValueInstance -> {
                String validationResult = validateChildObservation((Map<String, Object>) qGroupValueInstance, entityConceptMap);
                if (validationResult != null) errorMessages.add(validationResult);
            });
            if (errorMessages.isEmpty()) return null;
            return String.join("\n", errorMessages);
        } else {
            validateChildObservation((Map<String, Object>) qGroupValue, entityConceptMap);
        }
        return null;
    }

    private String validateChildObservation(Map<String, Object> qGroupValueInstance,
                                            LinkedHashMap<String, FormElement> entityConceptMap) {
        List<ObservationRequest> observationRequests = qGroupValueInstance.entrySet().stream().map(this::createObservationRequest).collect(Collectors.toList());
        return validateConceptValuesAreOfRequiredType(observationRequests, entityConceptMap);
    }

    private ObservationRequest createObservationRequest(Map.Entry<String, Object> stringObjectEntry) {
        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(stringObjectEntry.getKey());
        observationRequest.setValue(stringObjectEntry.getValue());
        return observationRequest;
    }

    private String formatErrorMessage(Concept question, Object value) {
        return String.format("Invalid value '%s' for %s concept name: %s, uuid:%s", value, ConceptDataType.valueOf(question.getDataType()), question.getName(), question.getUuid());
    }
}
