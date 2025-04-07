package org.avni.server.service;

import com.amazonaws.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.common.EnhancedValidationDTO;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.observation.PhoneNumberObservationValue;
import org.avni.server.util.*;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static org.avni.messaging.domain.Constants.DURATION_PATTERN;

@Service("EnhancedValidationService")
public class EnhancedValidationService {
    public static final boolean INCLUDE_VOIDED_FORM_ELEMENTS = true;
    private static final Logger logger = LoggerFactory.getLogger(EnhancedValidationService.class);
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;
    private final BugsnagReporter bugsnagReporter;
    private final ConceptRepository conceptRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
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
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public void validateObservationsAndDecisionsAgainstFormMapping(List<ObservationRequest> observationRequests, List<Decision> decisions, FormMapping formMapping) throws ValidationException {
        List<String> errorMessages = new ArrayList<>();
        LinkedHashMap<String, FormElement> formElements = formMappingService.getEntityConceptMap(formMapping, INCLUDE_VOIDED_FORM_ELEMENTS);

        checkForInvalidConceptUUIDAndNames(observationRequests, decisions, formMapping, formElements, errorMessages);
        validateConceptValuesAreOfRequiredType(observationRequests, formElements, formMapping, errorMessages);
        handleValidationFailure(errorMessages);
    }

    private void checkForInvalidConceptUUIDAndNames(List<ObservationRequest> observationRequests, List<Decision> decisions, FormMapping formMapping, LinkedHashMap<String, FormElement> formElements, List<String> errorMessages) {
        List<String> conceptUuids = getObservationConceptUuidsFromRequest(observationRequests);
        conceptUuids.addAll(getDecisionConceptUuidsFromRequest(decisions));

        List<String> nonMatchingConceptUuids = conceptUuids
                .stream()
                .filter(conceptUuid -> !formElements.containsKey(conceptUuid))
                .collect(Collectors.toList());

        if (!nonMatchingConceptUuids.isEmpty()) {
            errorMessages.add(String.format("Invalid concept uuids/names %s found for Form uuid/name: %s/%s", String.join(", ", nonMatchingConceptUuids), formMapping.getFormUuid(), formMapping.getFormName()));
        }
    }

    private void validateConceptValuesAreOfRequiredType(List<ObservationRequest> observationRequests, LinkedHashMap<String, FormElement> formElements, FormMapping formMapping, List<String> errorMessages) {
        observationRequests.stream()
                .map(observationRequest -> new EnhancedValidationDTO(conceptRepository.findByUuid(observationRequest.getConceptUUID()),
                        formElements.get(observationRequest.getConceptUUID()),
                        observationRequest.getValue()))
                .forEach(enhancedValidationDTO -> validate(enhancedValidationDTO, formMapping, errorMessages));
    }

    private void handleValidationFailure(List<String> errorMessages) throws ValidationException {
        if (errorMessages.isEmpty()) return;

        String errorMessage = String.join(", ", errorMessages);
        ValidationException validationException = new ValidationException(errorMessage);
        bugsnagReporter.logAndReportToBugsnag(validationException);
        if (organisationConfigService.isFailOnValidationErrorEnabled()) {
            throw validationException;
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
                .toList());

        return conceptUuids;
    }

    private List<String> getDecisionConceptUuidsFromRequest(List<Decision> decisions) {
        return decisions != null ? decisions
                .stream()
                .map(decision -> conceptRepository.findByName(decision.getName()))
                .map(Concept::getUuid)
                .collect(Collectors.toList()) : new ArrayList<>();
    }

    private void validate(EnhancedValidationDTO enhancedValidationDTO, FormMapping formMapping, List<String> errorMessages) {
        Object value = enhancedValidationDTO.getValue();
        if (enhancedValidationDTO.getConcept().isQuestionGroup()) {
            validateQuestionGroupConcept(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(),
                    value, formMapping, errorMessages);
        } else if (value instanceof Collection<?>) {
            ((Collection<Object>) value).forEach(vl -> {
                validateAnswer(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(), vl, errorMessages);
            });
            if (errorMessages.isEmpty()) return;
        } else {
            validateAnswer(enhancedValidationDTO.getConcept(), enhancedValidationDTO.getFormElement(), value, errorMessages);
        }
    }

    private void validateAnswer(Concept question, FormElement formElement, Object value, List<String> errorMessages) {
        if (value == null || (value instanceof String && ((String) value).trim().equals(""))) return;
        switch (ConceptDataType.valueOf(question.getDataType())) {
            case Coded:
                if (question.getConceptAnswers().stream().noneMatch(ans -> ans.getAnswerConcept().getUuid().equals(value))) {
                    errorMessages.add(String.format("Concept answer '%s' not found in Concept '%s'", value, question.getUuid()));
                }
                return;
            case Numeric:
                try {
                    Double.parseDouble(value.toString());
                } catch (NumberFormatException numberFormatException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Text:
                try {
                    String text = (String) value;
                    if (formElement.getValidFormat() != null) {
                        if (!text.matches(formElement.getValidFormat().getRegex())) {
                            errorMessages.add(formatErrorMessage(question, value));
                            return;
                        }
                    }
                } catch (ClassCastException classCastException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Date:
            case DateTime:
                try {
                    DateTimeUtil.parseNullableDateTime(value.toString());
                } catch (Exception e) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Duration:
                try {
                    String duration = (String) value;
                    if (!duration.matches(DURATION_PATTERN)) {
                        errorMessages.add(formatErrorMessage(question, value));
                    }
                } catch (ClassCastException classCastException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Time:
                try {
                    LocalTime.parse(value.toString());
                } catch (DateTimeParseException dateTimeParseException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Subject:
                SubjectType subjectType = subjectTypeRepository.findByUuid(question.getKeyValues().get(KeyType.subjectTypeUUID).getValue().toString());
                if (individualRepository.findByLegacyIdOrUuidAndSubjectType((String) value, subjectType) == null)
                    errorMessages.add(formatErrorMessage(question, value));
                return;
            case Location:
                try {
                    List<String> lowestLevelUuids = (List<String>) question.getKeyValues().get(KeyType.lowestAddressLevelTypeUUIDs).getValue();
                    List<AddressLevelType> lowestLevels = lowestLevelUuids.stream()
                            .map(addressLevelTypeRepository::findByUuid)
                            .toList();
                    if (!lowestLevels
                            .stream()
                            .map(AddressLevelType::getAddressLevels)
                            .flatMap(Collection::stream)
                            .map(AddressLevel::getUuid)
                            .toList()
                            .contains(value)) {
                        errorMessages.add(formatErrorMessage(question, value));
                    }
                } catch (ClassCastException classCastException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case PhoneNumber:
                try {
                    PhoneNumberObservationValue phoneNumber = objectMapper.convertValue(value, PhoneNumberObservationValue.class);
                    if (PhoneNumberUtil.isValidPhoneNumber(phoneNumber.getPhoneNumber(), RegionUtil.getCurrentUserRegion())) {
                        errorMessages.add(formatErrorMessage(question, value));
                    }
                } catch (ClassCastException | IllegalArgumentException e) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            case Image, ImageV2:
                try {
                    URL dummyUrl = s3Service.generateMediaUploadUrl("dummy.jpg", HttpMethod.PUT);
                    URL imageUrl = new URL(value.toString());
                    if (!Objects.equals(dummyUrl.getProtocol(), imageUrl.getProtocol()) || !Objects.equals(dummyUrl.getHost(), imageUrl.getHost())) {
                        errorMessages.add(formatErrorMessage(question, value));
                    }
                } catch (MalformedURLException malformedURLException) {
                    errorMessages.add(formatErrorMessage(question, value));
                }
                return;
            default:
        }
    }

    public void validateQuestionGroupConcept(Concept question, FormElement formElement, Object qGroupValue, FormMapping formMapping, List<String> errorMessages) {
        if (qGroupValue == null) {
            errorMessages.add(String.format("Null value specified for question group concept name: %s, uuid:%s", question.getName(), question.getUuid()));
        } else if (formElement.isRepeatable() && !(qGroupValue instanceof Collection<?>)) {
            errorMessages.add(String.format("Non-repeatable qGroupValue specified for Repeatable question group concept name: %s, uuid:%s", question.getName(), question.getUuid()));
        } else if (!formElement.isRepeatable() && qGroupValue instanceof Collection<?>) {
            errorMessages.add(String.format("Repeatable qGroupValue specified for Non-Repeatable question group concept name: %s, uuid:%s", question.getName(), question.getUuid()));
        }
        splitQuestionGroupValueIfRequiredAndThenValidate(formElement, qGroupValue, formMapping, errorMessages);
    }

    private void splitQuestionGroupValueIfRequiredAndThenValidate(FormElement formElement, Object qGroupValue, FormMapping formMapping, List<String> errorMessages) {
        if (qGroupValue instanceof Collection<?>) {
            ((Collection<Object>) qGroupValue).forEach(qGroupValueInstance -> {
                validateChildObservation(formElement, (Map<String, Object>) qGroupValueInstance, formMapping, errorMessages);
            });
        } else {
            validateChildObservation(formElement, (Map<String, Object>) qGroupValue, formMapping, errorMessages);
        }
    }

    private void validateChildObservation(FormElement questionGroupFormElement, Map<String, Object> qGroupValueInstance, FormMapping formMapping, List<String> errorMessages) {
        LinkedHashMap<String, FormElement> formElements = formMappingService.getEntityConceptMapForSpecificQuestionGroupFormElement(questionGroupFormElement, formMapping, INCLUDE_VOIDED_FORM_ELEMENTS);
        List<ObservationRequest> observationRequests = qGroupValueInstance.entrySet().stream().map(this::createObservationRequest).collect(Collectors.toList());
        List<String> nonMatchingConceptUuids = getObservationConceptUuidsFromRequest(observationRequests)
                .stream()
                .filter(conceptUuid -> !formElements.containsKey(conceptUuid))
                .collect(Collectors.toList());

        if (!nonMatchingConceptUuids.isEmpty()) {
            errorMessages.add(String.format("Invalid concept uuids/names %s found for questionGroupConcept uuid/name: %s/%s", String.join(", ", nonMatchingConceptUuids),
                    questionGroupFormElement.getConcept().getUuid(), questionGroupFormElement.getName()));
            return;
        }

        validateConceptValuesAreOfRequiredType(observationRequests, formElements, formMapping, errorMessages);
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
