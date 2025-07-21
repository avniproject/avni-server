package org.avni.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormElement;
import org.avni.server.application.KeyType;
import org.avni.server.application.KeyValues;
import org.avni.server.application.ValueType;
import org.avni.server.dao.AnswerConceptMigrationRepository;
import org.avni.server.dao.ConceptAnswerRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.domain.*;
import org.avni.server.util.*;
import org.avni.server.web.api.ApiRequestContextHolder;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.ReferenceDataContract;
import org.avni.server.web.request.application.ConceptUsageContract;
import org.avni.server.web.request.application.FormUsageContract;
import org.avni.server.web.response.Response;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Service
public class ConceptService implements NonScopeAwareService {
    public static final String STRING_CONSTANT_ANY_NUMBER = "Any Number";
    public static final String STRING_CONSTANT_ANY_TEXT = "Any Text";
    public static final String STRING_CONSTANT_DELIMITER = ", ";
    public static final String STRING_CONSTANT_PREFIX = "{";
    public static final String STRING_CONSTANT_SUFFIX = "}";
    public static final String STRING_CONSTANT_EXAMPLE_NUMBER = "123";
    public static final String STRING_CONSTANT_EXAMPLE_TEXT = "ABC";
    public static final String EMPTY_STRING = "";

    private final Logger logger;
    private final ConceptRepository conceptRepository;
    private final ConceptAnswerRepository conceptAnswerRepository;
    private final FormElementRepository formElementRepository;
    private final AnswerConceptMigrationRepository answerConceptMigrationRepository;
    private final LocationRepository locationRepository;

    @Autowired
    public ConceptService(ConceptRepository conceptRepository, ConceptAnswerRepository conceptAnswerRepository, FormElementRepository formElementRepository, AnswerConceptMigrationRepository answerConceptMigrationRepository, LocationRepository locationRepository) {
        this.formElementRepository = formElementRepository;
        this.answerConceptMigrationRepository = answerConceptMigrationRepository;
        this.locationRepository = locationRepository;
        logger = LoggerFactory.getLogger(this.getClass());
        this.conceptRepository = conceptRepository;
        this.conceptAnswerRepository = conceptAnswerRepository;
    }

    private static Map<String, String> readMap(String concepts) throws IOException {
        Map<String, String> jsonMap = new HashMap<>();
        ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
        if (!S.isEmpty(concepts)) {
            jsonMap = objectMapper.readValue(concepts, new TypeReference<Map<String, String>>() {
            });
        }
        return jsonMap;
    }

    private Concept fetchOrCreateConcept(ConceptContract conceptRequest, boolean ignoreAnswerConceptUUIDAbsence) {
        if (conceptRequest == null) {
            throw new BadRequestError("Concept request cannot be null");
        }
        conceptRequest.validate();
        assertNotDuplicate(conceptRequest);
        String uuid = conceptRequest.getUuid();
        String name = conceptRequest.getName();
        Concept concept = conceptRepository.findByUuid(uuid);

        if (concept == null && StringUtils.hasText(name)) {
            Concept existingConceptWithSameName = conceptRepository.findByName(name.trim());
            if (existingConceptWithSameName != null &&
                    (ignoreAnswerConceptUUIDAbsence ? StringUtils.hasText(uuid) : !existingConceptWithSameName.getUuid().equals(uuid))) {
                throw new BadRequestError(String.format("Concept with name '%s' already exists with different UUID: %s",
                        name.trim(), existingConceptWithSameName.getUuid()));
            }
            concept = existingConceptWithSameName;
        }

        if (concept == null) {
            concept = createConcept(uuid);
        }
        return concept;
    }

    private Concept createConcept(String uuid) {
        Concept concept = new Concept();
        concept.assignUUID(uuid);
        concept.setActive(true);
        return concept;
    }

    private boolean conceptExistsWithSameNameAndDifferentUUID(ConceptContract conceptRequest) {
        Concept concept = conceptRepository.findByName(conceptRequest.getName());
        return concept != null && !concept.getUuid().equals(conceptRequest.getUuid());
    }

    private ConceptAnswer fetchOrCreateConceptAnswer(Concept concept, ConceptContract answerConceptRequest, double answerOrder) {
        ConceptAnswer conceptAnswer = concept.findConceptAnswerByConceptUUIDOrName(answerConceptRequest.getUuid(), answerConceptRequest.getName());
        if (conceptAnswer == null) {
            conceptAnswer = new ConceptAnswer();
            conceptAnswer.assignUUID();
        }
        Concept answerConcept = conceptRepository.findByUuidOrName(answerConceptRequest.getUuid(), answerConceptRequest.getName());
        conceptAnswer.setAnswerConcept(answerConcept);
        Double providedOrder = answerConceptRequest.getOrder();
        conceptAnswer.setOrder(providedOrder == null ? answerOrder : providedOrder);
        conceptAnswer.setAbnormal(answerConceptRequest.isAbnormal());
        conceptAnswer.setUnique(answerConceptRequest.isUnique());
        conceptAnswer.setVoided(answerConceptRequest.isVoided());
        return conceptAnswer;
    }

    private void createCodedConcept(Concept concept, ConceptContract conceptRequest) {
        Set<ConceptAnswer> existingAnswers = concept.getConceptAnswers();
        List<ConceptContract> answers = (List<ConceptContract>) O.coalesce(conceptRequest.getAnswers(), new ArrayList<>());
        AtomicInteger index = new AtomicInteger(0);
        List<ConceptAnswer> conceptAnswers = new ArrayList<>();
        for (ConceptContract answerContract : answers) {
            ConceptAnswer conceptAnswer = fetchOrCreateConceptAnswer(concept, answerContract, (short) index.incrementAndGet());
            conceptAnswers.add(conceptAnswer);
        }
        concept.addAll(conceptAnswers);
        // clone existing answers to new list
        List<ConceptAnswer> existingAnswersList = new ArrayList<>(existingAnswers);
        existingAnswersList.forEach(existingAnswer -> {
            if (!conceptAnswers.contains(existingAnswer)) {
                ConceptAnswer removedAnswer = getAnswer(concept.getUuid(), existingAnswer.getAnswerConcept().getUuid());
                removedAnswer.setVoided(true);
                conceptAnswerRepository.save(removedAnswer);
                concept.removeAnswer(existingAnswer);
            }
        });
    }

    private void setRangeValues(Concept concept, ConceptContract conceptRequest) {
        concept.setHighAbsolute(conceptRequest.getHighAbsolute());
        concept.setLowAbsolute(conceptRequest.getLowAbsolute());
        concept.setHighNormal(conceptRequest.getHighNormal());
        concept.setLowNormal(conceptRequest.getLowNormal());
        concept.setUnit(conceptRequest.getUnit());
    }

    private String getDataType(ConceptContract conceptContract, Concept concept) {
        if (conceptContract.getDataType() == null) {
            return concept.isNew() ? ConceptDataType.NA.toString() : concept.getDataType();
        }
        return conceptContract.getDataType();
    }

    private void addToMigrationIfRequired(ConceptContract conceptRequest) {
        Concept concept = conceptRepository.findByUuid(conceptRequest.getUuid());
        boolean isNa = conceptRequest.getDataType() == null || conceptRequest.getDataType().equals(ConceptDataType.NA.name());
        if (isNa && concept != null && !concept.getName().equals(conceptRequest.getName())) {
            List<ConceptAnswer> conceptAnswers = conceptAnswerRepository.findByAnswerConcept(concept);
            List<AnswerConceptMigration> answerConceptMigrations = conceptAnswers.stream().map(ca -> {
                AnswerConceptMigration answerConceptMigration = new AnswerConceptMigration();
                answerConceptMigration.setConcept(ca.getConcept());
                answerConceptMigration.setOldAnswerConceptName(concept.getName());
                answerConceptMigration.setNewAnswerConceptName(conceptRequest.getName());
                answerConceptMigration.assignUUID();
                return answerConceptMigration;
            }).collect(Collectors.toList());
            answerConceptMigrationRepository.saveAll(answerConceptMigrations);
        }
    }

    private static void updateMediaInfo(ConceptContract conceptRequest, Concept concept, boolean ignoreConceptRequestMediaAbsence) {
        if (StringUtils.hasText(conceptRequest.getMediaUrl())) {
            concept.setMediaType(Concept.MediaType.Image);
            concept.setMediaUrl(conceptRequest.getMediaUrl());
        } else {
            if (ignoreConceptRequestMediaAbsence && StringUtils.hasText(concept.getMediaUrl())) {
                return; // Ignore if no media URL is provided, and we should not remove the existing media info
            } else {
                concept.setMediaType(null);
                concept.setMediaUrl(null);
            }
        }
    }

    public List<String> saveOrUpdateConcepts(List<ConceptContract> conceptRequests, ConceptContract.RequestType requestType) {
        ArrayList<Concept> concepts = new ArrayList<>();
        for (ConceptContract conceptRequest : conceptRequests) {
            logger.info("Processing concept: {} {}", conceptRequest.getName(), conceptRequest.getUuid());
            List<ConceptContract> answerConcepts = getAnswerConcepts(conceptRequest);
            for (ConceptContract answerConceptRequest : answerConcepts) {
                Concept answerConcept = fetchOrCreateConcept(answerConceptRequest, !requestType.equals(ConceptContract.RequestType.Bundle));
                String dataType = getDataType(answerConceptRequest, answerConcept);
                answerConcept.setName(answerConceptRequest.getName());
                answerConcept.setDataType(dataType);
                updateMediaInfo(answerConceptRequest, answerConcept, true);
                answerConcept.updateAudit();
                conceptRepository.save(answerConcept);
                addToMigrationIfRequired(answerConceptRequest);
            }

            Concept concept = fetchOrCreateConcept(conceptRequest, false);
            String dataType = getDataType(conceptRequest, concept);
            concept.setName(conceptRequest.getName());
            concept.setDataType(dataType);

            concept.setVoided(conceptRequest.isVoided());
            concept.setActive(conceptRequest.getActive());
            concept.setKeyValues(conceptRequest.getKeyValues());
            updateMediaInfo(conceptRequest, concept, false);
            concept.updateAudit();

            switch (ConceptDataType.valueOf(dataType)) {
                case Coded:
                    createCodedConcept(concept, conceptRequest);
                    break;
                case Numeric:
                    setRangeValues(concept, conceptRequest);
                    break;
            }

            concepts.add(conceptRepository.save(concept));
            addToMigrationIfRequired(conceptRequest);
        }
        return concepts.stream()
                .map(Concept::getUuid)
                .collect(Collectors.toList());
    }

    private void assertNotDuplicate(ConceptContract conceptRequest) {
        if (StringUtils.hasText(conceptRequest.getName()) && StringUtils.hasText(conceptRequest.getUuid()) && conceptExistsWithSameNameAndDifferentUUID(conceptRequest)) {
            Concept existingConcept = conceptRepository.findByName(conceptRequest.getName().trim());
            throw new BadRequestError(String.format("Concept with name '%s' already exists with different UUID: %s",
                    conceptRequest.getName(), existingConcept.getUuid()));
        }
    }

    private static List<ConceptContract> getAnswerConcepts(ConceptContract conceptRequest) {
        if (conceptRequest.getAnswers() != null) {
            return conceptRequest.getAnswers().stream().filter(ConceptContract::hasNameOrUUID).toList();
        }
        return List.of();
    }

    public Concept get(String uuid) {
        return conceptRepository.findByUuid(uuid);
    }

    public Concept getByName(String name) {
        return conceptRepository.findByName(name);
    }

    public ConceptAnswer getAnswer(String conceptUUID, String conceptAnswerUUID) {
        Concept concept = this.get(conceptUUID);
        Concept answerConcept = this.get(conceptAnswerUUID);
        return conceptAnswerRepository.findByConceptAndAnswerConcept(concept, answerConcept);
    }

    /**
     * Important: Not to be used in any Internal API calls
     */
    public Object getObservationValue(Concept questionConcept, Object value) {
        if (questionConcept.getDataType().equals(ConceptDataType.Date.toString())) {
            return ApiRequestContextHolder.isVersionGreaterThan(1) ? DateTimeUtil.toDateString((String) value) : value;
        }

        if (Arrays.asList(ConceptDataType.Audio, ConceptDataType.Id, ConceptDataType.Encounter, ConceptDataType.DateTime,
                        ConceptDataType.Duration, ConceptDataType.File, ConceptDataType.GroupAffiliation, ConceptDataType.Image,
                        ConceptDataType.ImageV2, ConceptDataType.NA, ConceptDataType.Notes, ConceptDataType.Numeric, ConceptDataType.PhoneNumber,
                        ConceptDataType.Subject, ConceptDataType.Text, ConceptDataType.Time, ConceptDataType.Video)
                .contains(ConceptDataType.valueOf(questionConcept.getDataType()))) {
            return value;
        }

        if (value instanceof ArrayList) {
            List<Object> answerElements = (List<Object>) value;
            return answerElements.stream().map(answersItem -> {
                if (answersItem instanceof String) { // Multi coded concept
                    Concept answerConcept = conceptRepository.findByUuid((String) answersItem);
                    return getNameWithDefaultValue(answerConcept, answersItem);
                } else if (answersItem instanceof HashMap) { // Repeatable question group
                    LinkedHashMap<String, Object> observationResponse = new LinkedHashMap<>();
                    Response.mapObservations(conceptRepository, this, observationResponse,
                            new ObservationCollection((HashMap<String, Object>) answersItem));
                    return observationResponse;
                } else {
                    return answersItem;
                }
            }).toArray();
        }

        if (value instanceof ObservationCollection) {
            LinkedHashMap<String, Object> observationResponse = new LinkedHashMap<>();
            Response.mapObservations(conceptRepository, this, observationResponse, (ObservationCollection) value);
            return observationResponse;
        }

        if (questionConcept.getDataType().equals(ConceptDataType.Location.toString())) {
            return checkAndReturnLocationAddress(value);
        }

        // Should be used single coded values only. Multi-coded handled above based on value
        if (value instanceof String && questionConcept.getDataType().equals(ConceptDataType.Coded.toString())) {
            Concept answerConcept = conceptRepository.findByUuid((String) value);
            return getNameWithDefaultValue(answerConcept, value);
        }

        return value;
    }

    private Object getNameWithDefaultValue(Concept answerConcept, Object obsRawValue) {
        return answerConcept == null ? obsRawValue : answerConcept.getName();
    }

    private Object checkAndReturnLocationAddress(Object value) {
        if (value != null && value instanceof String) {
            LinkedHashMap<String, String> location = new LinkedHashMap<>();
            AddressLevel addressLevel = locationRepository.findByLegacyIdOrUuid((String) value);
            if (addressLevel == null) {
                return value;
            }
            while (addressLevel != null) {
                putAddressLevel(location, addressLevel);
                addressLevel = addressLevel.getParent();
            }
            return location;
        } else {
            return value;
        }
    }

    private static void putAddressLevel(Map<String, String> map, AddressLevel addressLevel) {
        map.put(addressLevel.getTypeString(), addressLevel.getTitle());
    }

    public void addDependentConcepts(ConceptUsageContract conceptUsageContract, Concept answerConcept) {
        List<ConceptAnswer> conceptAnswers = conceptAnswerRepository.findByAnswerConcept(answerConcept);
        conceptAnswers.forEach(ca -> {
            ReferenceDataContract conceptContract = new ReferenceDataContract();
            Concept concept = ca.getConcept();
            conceptContract.setName(concept.getName());
            conceptContract.setId(concept.getId());
            conceptContract.setUuid(concept.getUuid());
            conceptContract.setVoided(ca.isVoided());
            conceptUsageContract.addConcepts(conceptContract);
            addDependentFormDetails(conceptUsageContract, concept);
        });
    }

    public void addDependentFormDetails(ConceptUsageContract conceptUsageContract, Concept concept) {
        List<FormElement> formElements = formElementRepository.findAllByConceptUuidAndIsVoidedFalse(concept.getUuid());
        formElements.forEach(formElement -> conceptUsageContract.addForms(FormUsageContract.fromEntity(formElement)));
    }

    public Map<Concept, String> readConceptsFromJsonObject(String jsonObject) {
        Map<Concept, String> jsonMap = new HashMap<>();
        try {
            Map<String, String> conceptsMap = readMap(jsonObject);
            for (Map.Entry<String, String> entry : conceptsMap.entrySet()) {
                String conceptName = entry.getKey();
                String value = entry.getValue();
                Concept concept = conceptRepository.findByName(conceptName);
                if (concept == null)
                    throw new BadRequestError("Bad Request: One of the specified concept(%s) does not exist", conceptName);
                jsonMap.put(concept, value);
            }
            return jsonMap;
        } catch (IOException e) {
            logger.error("Bad Request", e);
            throw new BadRequestError("Bad Request: concepts parameter is not a valid json object");
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return conceptRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public Optional<Concept> findContactNumberConcept() {
        List<Concept> textConcepts = conceptRepository.findByIsVoidedFalseAndDataType("Text");
        return textConcepts.stream().filter(textConcept -> {
            KeyValues keyValues = textConcept.getKeyValues();
            ValueType[] valueTypes = {ValueType.yes};
            return (keyValues != null && (keyValues.containsOneOfTheValues(KeyType.contact_number, valueTypes) ||
                    keyValues.containsOneOfTheValues(KeyType.primary_contact, valueTypes)));
        }).findFirst();
    }

    public String getAllowedValuesForSyncConcept(Concept concept) {
        switch (ConceptDataType.valueOf(concept.getDataType())) {
            case Numeric:
                return STRING_CONSTANT_ANY_NUMBER;
            case Text:
                return STRING_CONSTANT_ANY_TEXT;
            case Coded:
                return concept.getSortedAnswers().map(sca -> sca.getAnswerConcept().getName())
                        .collect(Collectors.joining(STRING_CONSTANT_DELIMITER, STRING_CONSTANT_PREFIX, STRING_CONSTANT_SUFFIX));
            default:
                return String.format("Appropriate value for a %s type concept", concept.getDataType());
        }
    }

    public String getExampleValuesForSyncConcept(Concept concept) {
        switch (ConceptDataType.valueOf(concept.getDataType())) {
            case Numeric:
                return STRING_CONSTANT_EXAMPLE_NUMBER;
            case Text:
                return STRING_CONSTANT_EXAMPLE_TEXT;
            case Coded:
                return concept.getSortedAnswers().map(sca -> sca.getAnswerConcept().getName()).findAny().get();
            default:
                return EMPTY_STRING;
        }
    }
}
