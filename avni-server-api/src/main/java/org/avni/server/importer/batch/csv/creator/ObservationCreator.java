package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.*;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.avni.server.util.*;
import org.avni.server.web.request.ObservationRequest;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Component
public class ObservationCreator {
    private static final Logger logger = LoggerFactory.getLogger(ObservationCreator.class);
    private final ConceptRepository conceptRepository;
    private final FormRepository formRepository;
    private final ObservationService observationService;
    private final S3Service s3Service;
    private final IndividualService individualService;
    private final LocationService locationService;
    private final FormElementRepository formElementRepository;

    @Autowired
    public ObservationCreator(ConceptRepository conceptRepository,
                              FormRepository formRepository,
                              ObservationService observationService,
                              S3Service s3Service,
                              IndividualService individualService,
                              LocationService locationService,
                              FormElementRepository formElementRepository,
                              EnhancedValidationService enhancedValidationService) {
        this.conceptRepository = conceptRepository;
        this.formRepository = formRepository;
        this.observationService = observationService;
        this.s3Service = s3Service;
        this.individualService = individualService;
        this.locationService = locationService;
        this.formElementRepository = formElementRepository;
    }

    public Set<Concept> getConceptsInHeader(HeaderCreator headers, FormMapping formMapping, String[] fileHeaders) {
        String[] conceptHeaders = headers.getConceptHeaders(formMapping, fileHeaders);
        return Arrays.stream(conceptHeaders)
                .map(name -> this.findConcept(S.unDoubleQuote(name), false))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Concept findConcept(String name, boolean isChildQuestionGroup) {
        Concept concept = conceptRepository.findByName(name);
        if (concept == null && name.contains("|")) {
            String[] parentChildNameArray = name.split("\\|");
            String questionGroupConceptName = isChildQuestionGroup ? parentChildNameArray[1] : parentChildNameArray[0];
            concept = conceptRepository.findByName(questionGroupConceptName);
        }
        return concept;
    }

    public ObservationCollection getObservations(Row row,
                                                 HeaderCreator headers,
                                                 List<String> errorMsgs, FormType formType, ObservationCollection oldObservations, FormMapping formMapping) throws ValidationException {
        ObservationCollection observationCollection = constructObservations(row, headers, errorMsgs, formType,
                oldObservations, formMapping, row.getHeaders(), false);
        ValidationUtil.handleErrors(errorMsgs);
        return observationCollection;
    }

    private String getRowValue(FormElement formElement, Row row, Integer questionGroupIndex) {
        Concept concept = formElement.getConcept();
        if (formElement.getGroup() != null) {
            Concept parentConcept = formElement.getGroup().getConcept();
            String parentChildName = parentConcept.getName() + "|" + concept.getName();
            String headerName = questionGroupIndex == null ? parentChildName : String.format("%s|%d", parentChildName, questionGroupIndex);
            return row.get(headerName);
        }
        return row.getObservation(concept.getName());
    }

    private ObservationCollection constructObservations(Row row, HeaderCreator headers, List<String> errorMsgs, FormType formType, ObservationCollection oldObservations, FormMapping formMapping, String[] fileHeaders, boolean performMandatoryCheck) {
        List<ObservationRequest> observationRequests = new ArrayList<>();
        Set<Concept> conceptsInHeader = getConceptsInHeader(headers, formMapping, fileHeaders);
        for (Concept concept : conceptsInHeader) {
            FormElement formElement = getFormElementForObservationConcept(concept, formType, formMapping);
            String rowValue = getRowValue(formElement, row, null);

            if (!StringUtils.hasText(rowValue)) {
                if (performMandatoryCheck && formElement.isMandatory()) {
                    errorMsgs.add(String.format("Value required for mandatory field '%s'", concept.getName()));
                }
                if (!concept.isQuestionGroup()) {
                    continue;
                }
            }

            if (!StringUtils.hasText(rowValue) && !formElement.isQuestionGroupElement()) {
                continue;
            }

            ObservationRequest observationRequest = new ObservationRequest();
            observationRequest.setConceptName(concept.getName());
            observationRequest.setConceptUUID(concept.getUuid());
            try {
                Object observationValue = getObservationValue(formElement, rowValue, formType, errorMsgs, row, headers, oldObservations, performMandatoryCheck);
                observationRequest.setValue(observationValue);
            } catch (RuntimeException ex) {
                logger.error(String.format("Error processing observation %s in row %s", rowValue, row), ex);
                errorMsgs.add(String.format("Invalid answer '%s' for '%s'", rowValue, concept.getName()));
            }
            observationRequests.add(observationRequest);
        }
        return observationService.createObservations(observationRequests);
    }

    private Object constructChildObservations(Row row, HeaderCreator headers, List<String> errorMsgs,
                                              FormElement parentFormElement, FormType formType, ObservationCollection oldObservations,
                                              boolean mandatoryCheckEnabled) {
        List<FormElement> allChildQuestions = formElementRepository.findAllByGroupId(parentFormElement.getId());
        if (parentFormElement.isRepeatable()) {
            Pattern repeatableQuestionGroupPattern = Pattern.compile(String.format("%s\\|.*\\|\\d", parentFormElement.getConcept().getName()));
            List<String> repeatableQuestionGroupHeaders = Stream.of(row.getHeaders())
                    .filter(repeatableQuestionGroupPattern.asPredicate())
                    .toList();
            int maxIndex = repeatableQuestionGroupHeaders.stream().map(fen -> Integer.valueOf(fen.split("\\|")[2]))
                    .mapToInt(v -> v)
                    .max().orElse(1);
            List<ObservationCollection> repeatableObservationRequest = new ArrayList<>();
            for (int i = 1; i <= maxIndex; i++) {
                ObservationCollection questionGroupObservations = getQuestionGroupObservations(row, headers, errorMsgs,
                                    formType, oldObservations, allChildQuestions, i, mandatoryCheckEnabled);
                if (!questionGroupObservations.isEmpty()) {
                    repeatableObservationRequest.add(questionGroupObservations);
                }
            }
            return repeatableObservationRequest;
        }
        return getQuestionGroupObservations(row, headers, errorMsgs, formType, oldObservations, allChildQuestions, null, mandatoryCheckEnabled);
    }

    private ObservationCollection getQuestionGroupObservations(Row row, HeaderCreator headers, List<String> errorMsgs, FormType formType, ObservationCollection oldObservations, List<FormElement> allChildQuestions, Integer questionGroupIndex, boolean mandatoryCheckEnabled) {
        List<ObservationRequest> observationRequests = new ArrayList<>();
        for (FormElement formElement : allChildQuestions) {
            Concept concept = formElement.getConcept();
            String rowValue = getRowValue(formElement, row, questionGroupIndex);
            if (!StringUtils.hasText(rowValue)) {
                if (mandatoryCheckEnabled && formElement.isMandatory()) {
                    errorMsgs.add(String.format("Value required for mandatory field '%s'", concept.getName()));
                }
                continue;
            }
            ObservationRequest observationRequest = new ObservationRequest();
            observationRequest.setConceptName(concept.getName());
            observationRequest.setConceptUUID(concept.getUuid());
            try {
                observationRequest.setValue(getObservationValue(formElement, rowValue, formType, errorMsgs, row, headers, oldObservations, mandatoryCheckEnabled));
            } catch (Exception ex) {
                logger.error(String.format("Error processing observation %s in row %s", rowValue, row), ex);
                errorMsgs.add(String.format("Invalid answer '%s' for '%s'", rowValue, concept.getName()));
            }
            observationRequests.add(observationRequest);
        }
        return observationService.createObservations(observationRequests);
    }

    private List<FormElement> createDecisionFormElement(Set<Concept> concepts) {
        return concepts.stream().map(dc -> {
            FormElement formElement = new FormElement();
            formElement.setType(dc.getDataType().equals(ConceptDataType.Coded.name()) ? FormElementType.MultiSelect.name() : FormElementType.SingleSelect.name());
            formElement.setConcept(dc);
            return formElement;
        }).collect(Collectors.toList());
    }

    private FormElement getFormElementForObservationConcept(Concept concept, FormType formType, FormMapping formMapping) {
        List<Form> applicableForms = formMapping != null ? Collections.singletonList(formMapping.getForm()) : formRepository.findByFormTypeAndIsVoidedFalse(formType);
        if (applicableForms.isEmpty())
            throw new RuntimeException(String.format("No forms of type %s found", formType));

        return applicableForms.stream()
                .map(f -> {
                    List<FormElement> formElements = f.getAllFormElements();
                    formElements.addAll(createDecisionFormElement(f.getDecisionConcepts()));
                    return formElements;
                })
                .flatMap(List::stream)
                .filter(formElement -> formElement.getConcept().equals(concept))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No form element linked to concept found"));
    }

    private Object getObservationValue(FormElement formElement, String answerValue, FormType formType, List<String> errorMsgs, Row row, HeaderCreator headers, ObservationCollection oldObservations, boolean mandatoryCheckEnabled) {
        Concept concept = formElement.getConcept();
        Object oldValue = oldObservations == null ? null : oldObservations.getOrDefault(concept.getUuid(), null);
        ConceptDataType dataType = ConceptDataType.valueOf(concept.getDataType());

        switch (dataType) {
            case Coded:
                return handleCodedValue(formElement, answerValue, errorMsgs, concept);
            case Numeric:
                return handleNumericValue(answerValue, errorMsgs, concept);
            case Date:
                return handleDateValue(answerValue, errorMsgs, concept);
            case DateTime:
                return handleDateTimeValue(answerValue, errorMsgs, concept);
            case Image, ImageV2, Video, Signature:
                return handleMediaValue(formElement, answerValue, errorMsgs, oldValue);
            case Subject:
                return individualService.getObservationValueForUpload(formElement, answerValue);
            case Location:
                return locationService.getObservationValueForUpload(formElement, answerValue);
            case PhoneNumber:
                return handlePhoneNumberValue(answerValue, errorMsgs, concept);
            case QuestionGroup:
                return this.constructChildObservations(row, headers, errorMsgs, formElement, formType, null, mandatoryCheckEnabled);
            default:
                return answerValue;
        }
    }

    private Object handleCodedValue(FormElement formElement, String answerValue, List<String> errorMsgs, Concept concept) {
                if (formElement.getType().equals(FormElementType.MultiSelect.name())) {
                    String[] providedAnswers = S.splitMultiSelectAnswer(answerValue);
                    return Stream.of(providedAnswers)
                            .map(String::trim)
                            .map(answer -> {
                                Concept answerConcept = concept.findAnswerConcept(answer);
                                if (answerConcept == null) {
                                    errorMsgs.add(format("Invalid answer '%s' for '%s'", answer, concept.getName()));
                                    return null;
                                }
                                return answerConcept.getUuid();
                            })
                            .collect(Collectors.toList());
                } else {
                    Concept answerConcept = concept.findAnswerConcept(answerValue.trim());
                    if (answerConcept == null) {
                        errorMsgs.add(format("Invalid answer '%s' for '%s'", answerValue.trim(), concept.getName()));
                        return null;
                    }
                    return answerConcept.getUuid();
                }
    }

    private Object handleNumericValue(String answerValue, List<String> errorMsgs, Concept concept) {
                try {
            double value = Double.parseDouble(answerValue);
            Double lowAbsolute = concept.getLowAbsolute();
            Double highAbsolute = concept.getHighAbsolute();

            if (!isWithinRange(value, lowAbsolute, highAbsolute)) {
                errorMsgs.add(format("Invalid answer '%s' for '%s'", answerValue, concept.getName()));
                return null;
            }
            return value;
                } catch (NumberFormatException e) {
                    errorMsgs.add(format("Invalid value '%s' for '%s'", answerValue, concept.getName()));
                    return null;
                }
    }

    private boolean isWithinRange(double value, Double lowAbsolute, Double highAbsolute) {
        if (lowAbsolute != null && highAbsolute != null) {
            return value >= lowAbsolute && value <= highAbsolute;
        } else if (lowAbsolute != null) {
            return value >= lowAbsolute;
        } else if (highAbsolute != null) {
            return value <= highAbsolute;
        }
        return true;
    }

    private Object handleDateValue(String answerValue, List<String> errorMsgs, Concept concept) {
                try {
                    String trimmed = answerValue.trim();
                    return (trimmed.isEmpty()) ? null : DateTimeUtil.parseFlexibleDate(trimmed);
                } catch (IllegalArgumentException e) {
                    errorMsgs.add(format("Invalid value '%s' for '%s'", answerValue, concept.getName()));
                    return null;
                }
    }

    private Object handleDateTimeValue(String answerValue, List<String> errorMsgs, Concept concept) {
                try {
                    return (answerValue.trim().isEmpty()) ? null : toISODateFormat(answerValue);
        } catch (IllegalArgumentException e) {
                    errorMsgs.add(format("Invalid value '%s' for '%s'", answerValue, concept.getName()));
                    return null;
                }
    }

    private Object handleMediaValue(FormElement formElement, String answerValue, List<String> errorMsgs, Object oldValue) {
                if (formElement.getType().equals(FormElementType.MultiSelect.name())) {
                    String[] providedURLs = S.splitMultiSelectAnswer(answerValue);
                    return Stream.of(providedURLs)
                            .map(url -> getMediaObservationValue(url, errorMsgs, null))
                            .collect(Collectors.toList());
                } else {
                    return getMediaObservationValue(answerValue, errorMsgs, oldValue);
                }
        }

    private Object handlePhoneNumberValue(String answerValue, List<String> errorMsgs, Concept concept) {
        String trimmedValue = answerValue.trim();
        return (trimmedValue.isEmpty()) ? null : toPhoneNumberFormat(trimmedValue, errorMsgs, concept.getName());
    }

    private Object getMediaObservationValue(String answerValue, List<String> errorMsgs, Object oldValue) {
        try {
            return s3Service.getObservationValueForUpload(answerValue, oldValue);
        } catch (Exception e) {
            errorMsgs.add(e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toPhoneNumberFormat(String phoneNumber, List<String> errorMsgs, String conceptName) {
        Map<String, Object> phoneNumberObs = new HashMap<>();
        if (!PhoneNumberUtil.isValidPhoneNumber(phoneNumber, RegionUtil.getCurrentUserRegion())) {
            errorMsgs.add(format("Invalid %s provided %s. Please provide valid phone number.", conceptName, phoneNumber));
            return null;
        }
        phoneNumberObs.put("phoneNumber", PhoneNumberUtil.getNationalPhoneNumber(phoneNumber, RegionUtil.getCurrentUserRegion()));
        phoneNumberObs.put("verified", false);
        return phoneNumberObs;
    }

    private String toISODateFormat(String dateStr) {
        DateTimeFormatter outputFmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        // Try each format in sequence
        DateTime dt = tryParseDateTime(dateStr);
        if (dt == null) {
            // If we couldn't parse it, throw a descriptive exception
            throw new IllegalArgumentException("Unable to parse date: " + dateStr +
                    ". Supported formats are: yyyy-MM-dd HH:mm:ss, dd-MM-yyyy, yyyy-MM-dd");
        }

        return dt.toString(outputFmt);
    }

    private DateTime tryParseDateTime(String dateStr) {
        // Try parsing with time in ISO format
        try {
            return DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (IllegalArgumentException ignored) {
            // Continue to next format
        }

        // Try European date format (dd-MM-yyyy)
        try {
            return DateTime.parse(dateStr, DateTimeFormat.forPattern("dd-MM-yyyy"))
                    .withHourOfDay(0)
                    .withMinuteOfHour(0)
                    .withSecondOfMinute(0);
        } catch (IllegalArgumentException ignored) {
            // Continue to next format
        }

        // Try ISO date format (yyyy-MM-dd)
        try {
            return DateTime.parse(dateStr, DateTimeFormat.forPattern("yyyy-MM-dd"))
                    .withHourOfDay(0)
                    .withMinuteOfHour(0)
                    .withSecondOfMinute(0);
        } catch (IllegalArgumentException ignored) {
            // Failed all attempts
            return null;
        }
    }
}
