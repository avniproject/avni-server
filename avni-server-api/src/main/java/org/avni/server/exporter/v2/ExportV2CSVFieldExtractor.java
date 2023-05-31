package org.avni.server.exporter.v2;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementType;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.exporter.ExportJobService;
import org.avni.server.service.AddressLevelService;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.ObservationService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportFilters;
import org.avni.server.web.external.request.export.ExportOutput;
import org.joda.time.DateTime;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileHeaderCallback;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@StepScope
public class ExportV2CSVFieldExtractor implements FieldExtractor<LongitudinalExportItemRow>, FlatFileHeaderCallback {
    private static final String selectedAnswerFieldValue = "1";
    private static final String unSelectedAnswerFieldValue = "0";
    private static final String EMPTY_STRING = "";
    private final ExportJobParametersRepository exportJobParametersRepository;
    private final EncounterRepository encounterRepository;
    private final ProgramEncounterRepository programEncounterRepository;
    private final FormMappingService formMappingService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final String exportJobParamsUUID;
    private String timeZone;
    private final AddressLevelService addressLevelService;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ExportJobService exportJobService;
    private final ObservationService observationService;
    private HeaderCreator headerCreator;
    private ExportOutput exportOutput;
    private List<String> addressLevelTypes = new ArrayList<>();
    private ExportFieldsManager exportFieldsManager;
    private Map<FormElement, Integer> maxNumberOfQuestionGroupObservations;

    @Autowired
    public ExportV2CSVFieldExtractor(EncounterRepository encounterRepository,
                                     ProgramEncounterRepository programEncounterRepository,
                                     FormMappingService formMappingService,
                                     @Value("#{jobParameters['exportJobParamsUUID']}") String exportJobParamsUUID,
                                     SubjectTypeRepository subjectTypeRepository,
                                     AddressLevelService addressLevelService,
                                     ProgramRepository programRepository,
                                     EncounterTypeRepository encounterTypeRepository,
                                     ExportJobService exportJobService,
                                     ObservationService observationService,
                                     ExportJobParametersRepository exportJobParametersRepository) {
        this.encounterRepository = encounterRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.formMappingService = formMappingService;
        this.exportJobParamsUUID = exportJobParamsUUID;
        this.subjectTypeRepository = subjectTypeRepository;
        this.addressLevelService = addressLevelService;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.exportJobService = exportJobService;
        this.observationService = observationService;
        this.exportJobParametersRepository = exportJobParametersRepository;
    }

    @PostConstruct
    public void init() {
        this.addressLevelTypes = addressLevelService.getAllAddressLevelTypeNames();
        ExportJobParameters exportJobParameters = exportJobParametersRepository.findByUuid(exportJobParamsUUID);
        this.timeZone = exportJobParameters.getTimezone();
        exportOutput = exportJobService.getExportOutput(exportJobParamsUUID);
        exportFieldsManager = new ExportFieldsManager(formMappingService, encounterRepository, programEncounterRepository, timeZone);
        exportOutput.accept(exportFieldsManager);
        Map<Form, ExportFilters> formFilters = exportFieldsManager.getAllFormFilters();
        maxNumberOfQuestionGroupObservations = observationService.getMaxNumberOfQuestionGroupObservations(formFilters, timeZone);
        this.headerCreator = new HeaderCreator(subjectTypeRepository, addressLevelTypes, maxNumberOfQuestionGroupObservations,
                encounterTypeRepository, exportFieldsManager, programRepository);
    }

    @Override
    public void writeHeader(Writer writer) throws IOException {
        exportOutput.accept(headerCreator);
        writer.write(this.headerCreator.getHeader());
    }

    public ExportOutput getExportOutput() {
        return exportOutput;
    }

    @Override
    public Object[] extract(LongitudinalExportItemRow individual) {
        return createRow(individual);
    }

    private Object[] createRow(LongitudinalExportItemRow itemRow) {
        List<Object> columnsData = new ArrayList<>();
        addRegistrationColumns(columnsData, itemRow.getIndividual(), exportFieldsManager.getMainFields(exportOutput), exportFieldsManager.getCoreFields(exportOutput));
        exportOutput.getEncounters().forEach(enc -> {
            List<Encounter> encounters = itemRow.getEncounterTypeToEncountersMap().get(enc.getUuid());
            if (encounters != null) {
                addEncounterColumns(exportFieldsManager.getMaxEntityCount(enc), columnsData, encounters,
                        exportFieldsManager.getMainFields(enc), exportFieldsManager.getSecondaryFields(enc), enc);
            } else {
                addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(enc));
            }
        });
        exportOutput.getGroups().forEach(grp -> {
            String groupSubjectTypeUUID = grp.getUuid();
            Map<Individual, Map<String, List<Encounter>>> groupSubjectToEncountersMap = itemRow.getGroupSubjectToEncountersMap();
            Optional<Individual> groupSubjectOptional = groupSubjectToEncountersMap.keySet().stream()
                    .filter(individual -> individual.getSubjectType().getUuid().equals(groupSubjectTypeUUID)).findFirst();
            if (groupSubjectOptional.isPresent()) {
                groupSubjectOptional.ifPresent(individual -> {
                    addRegistrationColumns(columnsData, individual, exportFieldsManager.getMainFields(grp), exportFieldsManager.getCoreFields(grp));
                    Map<String, List<Encounter>> encounters = groupSubjectToEncountersMap.get(individual);
                    grp.getEncounters().forEach(ge -> {
                        if (encounters != null && encounters.get(ge.getUuid()) != null) {
                            addEncounterColumns(exportFieldsManager.getMaxEntityCount(ge), columnsData, encounters.get(ge.getUuid()),
                                    exportFieldsManager.getMainFields(ge), exportFieldsManager.getSecondaryFields(ge), ge);
                        } else {
                            addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(ge));
                        }
                    });
                });
            } else {
                addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(grp));
                addBlanks(columnsData, addressLevelTypes.size());//Blanks for GroupSubject addressLevels
                addBlanks(columnsData, 1);//Blanks for No of Group members
            }
        });
        Map<ProgramEnrolment, Map<String, List<ProgramEncounter>>> programEnrolmentToEncountersMap = itemRow.getProgramEnrolmentToEncountersMap();
        exportOutput.getPrograms().forEach(program -> {
            Optional<ProgramEnrolment> programEnrolmentOptional = programEnrolmentToEncountersMap.keySet().stream().filter(pe -> pe.getProgram().getUuid().equals(program.getUuid())).findFirst();
            if (programEnrolmentOptional.isPresent()) {
                programEnrolmentOptional.ifPresent(programEnrolment -> {
                    addEnrolmentColumns(columnsData, programEnrolment, exportFieldsManager.getMainFields(program), exportFieldsManager.getSecondaryFields(program), program);
                    program.getEncounters().forEach(pe -> {
                        Map<String, List<ProgramEncounter>> encounterTypeListMap = programEnrolmentToEncountersMap.get(programEnrolment);
                        if (encounterTypeListMap != null && encounterTypeListMap.get(pe.getUuid()) != null) {
                            addEncounterColumns(exportFieldsManager.getMaxEntityCount(pe), columnsData, encounterTypeListMap.get(pe.getUuid()), exportFieldsManager.getMainFields(pe),
                                    exportFieldsManager.getSecondaryFields(pe), pe);
                        } else {
                            addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(pe));
                        }
                    });
                });
            } else {
                addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(program));
            }
        });
        return columnsData.toArray();
    }

    private void addRegistrationColumns(List<Object> columnsData, Individual individual, Map<String, FormElement> registrationMap, List<String> coreFields) {
        addStaticRegistrationColumns(columnsData, individual, HeaderCreator.registrationDataMap, coreFields);
        addAddressLevels(columnsData, individual.getAddressLevel());
        if (individual.getSubjectType().isGroup()) {
            columnsData.add(getTotalMembers(individual));
        }
        columnsData.addAll(addObservations(individual.getObservations(), registrationMap));
    }

    private void addStaticRegistrationColumns(List<Object> columnsData, Individual individual,
                                              Map<String, HeaderNameAndFunctionMapper<Individual>> registrationDataMap, List<String> fields) {
        fields.stream()
                .filter(registrationDataMap::containsKey)
                .forEach(key -> columnsData.add(registrationDataMap.get(key).getValueFunction().apply(individual)));
    }

    private void addEnrolmentColumns(List<Object> columnsData, ProgramEnrolment programEnrolment, Map<String, FormElement> enrolmentMap,
                                     Map<String, FormElement> exitEnrolmentMap,
                                     ExportOutput.ExportNestedOutput program) {
        addStaticEnrolmentColumns(program, columnsData, programEnrolment, HeaderCreator.enrolmentDataMap);
        columnsData.addAll(addObservations(programEnrolment.getObservations(), enrolmentMap));
        columnsData.addAll(addObservations(programEnrolment.getObservations(), exitEnrolmentMap));
    }

    private <T extends AbstractEncounter> void addEncounterColumns(Long maxVisitCount, List<Object> columnsData, List<T> encounters,
                                                                   Map<String, FormElement> map, Map<String, FormElement> cancelMap, ExportEntityType encounterEntityType) {
        AtomicInteger counter = new AtomicInteger(0);
        encounters.forEach(encounter -> {
            appendStaticEncounterColumns(encounterEntityType, columnsData, encounter, HeaderCreator.encounterDataMap);
            columnsData.addAll(addObservations(encounter.getObservations(), map));
            columnsData.addAll(addObservations(encounter.getObservations(), cancelMap));
            counter.getAndIncrement();
        });
        int visit = counter.get();
        while (visit++ < maxVisitCount) {
            addBlanks(columnsData, exportFieldsManager.getTotalNumberOfColumns(encounterEntityType));
        }
    }

    private void addStaticEnrolmentColumns(ExportOutput.ExportNestedOutput program, List<Object> columnsData, ProgramEnrolment programEnrolment,
                                           Map<String, HeaderNameAndFunctionMapper<ProgramEnrolment>> enrolmentDataMap) {
        exportFieldsManager.getCoreFields(program).stream().filter(enrolmentDataMap::containsKey)
                .forEach(key -> columnsData.add(enrolmentDataMap.get(key).getValueFunction().apply(programEnrolment)));
    }

    private void appendStaticEncounterColumns(ExportEntityType encounterEntityType, List<Object> columnsData, AbstractEncounter encounter,
                                              Map<String, HeaderNameAndFunctionMapper<AbstractEncounter>> encounterDataMap) {
        exportFieldsManager.getCoreFields(encounterEntityType).stream().filter(encounterDataMap::containsKey)
                .forEach(key -> columnsData.add(encounterDataMap.get(key).getValueFunction().apply(encounter)));
    }

    private long getTotalMembers(Individual individual) {
        return individual.getGroupSubjects()
                .stream()
                .filter(gs -> gs.getMembershipEndDate() == null && !gs.getMemberSubject().isVoided())
                .count();
    }

    private List<Object> addObservations(ObservationCollection observations, Map<String, FormElement> obsMap) {
        List<Object> values = new ArrayList<>(obsMap.size());
        obsMap.forEach((conceptUUID, formElement) -> {
            if (formElement.isPartOfRepeatableQuestionGroup() || formElement.isQuestionGroupElement()) return;

            Object val;
            if (formElement.getGroup() != null) {
                Concept parentConcept = formElement.getGroup().getConcept();
                Map<String, Object> nestedObservations = observations == null ? Collections.EMPTY_MAP : (Map<String, Object>) observations.getOrDefault(parentConcept.getUuid(), new HashMap<String, Object>());
                val = nestedObservations.getOrDefault(conceptUUID, null);
            } else {
                val = observations == null ? null : observations.getOrDefault(conceptUUID, null);
            }

            addObservation(values, formElement, val);
        });

        List<FormElement> observationsRepeatedMultipleTimes = obsMap.values().stream()
                .filter(FormElement::isPartOfRepeatableQuestionGroup)
                .collect(Collectors.toList());
        Map<FormElement, List<FormElement>> repeatedFormElements = ExportFieldsManager.groupByQuestionGroup(observationsRepeatedMultipleTimes);
        repeatedFormElements.forEach((group, formElements) -> {
            Integer maxRepeats = maxNumberOfQuestionGroupObservations.get(group);
            Concept questionGroupConcept = group.getConcept();
            List<Map<String, Object>> repeatableObservations;
            if (observations == null) repeatableObservations = new ArrayList<>();
            else {
                repeatableObservations = (List<Map<String, Object>>) observations.getOrDefault(questionGroupConcept.getUuid(), new ArrayList<>());
            }
            for (int i = 0; i < maxRepeats; i++) {
                if (repeatableObservations.size() > i) {
                    Map<String, Object> observationsItem = repeatableObservations.get(i);
                    for (FormElement formElement : formElements) {
                        Object val = observationsItem.getOrDefault(formElement.getConcept().getUuid(), null);
                        addObservation(values, formElement, val);
                    }
                } else {
                    formElements.forEach(formElement -> values.add(""));
                }
            }
        });
        return values;
    }

    private void addObservation(List<Object> values, FormElement formElement, Object val) {
        String dataType = formElement.getConcept().getDataType();
        if (dataType.equals(ConceptDataType.Coded.toString())) {
            values.addAll(processCodedObs(formElement.getType(), val, formElement));
        } else if (dataType.equals(ConceptDataType.DateTime.toString()) || dataType.equals(ConceptDataType.Date.toString())) {
            values.add(processDateObs(val));
        } else if (ConceptDataType.isMedia(dataType)) {
            values.add(processMediaObs(val));
        } else {
            values.add(getFieldValue(String.valueOf(Optional.ofNullable(val).orElse(""))));
        }
    }

    private Object processDateObs(Object val) {
        if (val == null) return "";
        return DateTimeUtil.getDateForTimeZone(new DateTime(String.valueOf(val)), timeZone);
    }

    private List<Object> processCodedObs(String formType, Object val, FormElement formElement) {
        List<Object> values = new ArrayList<>();
        if (formType.equals(FormElementType.MultiSelect.toString())) {
            List<Object> codedObs = getObservationValueList(val);
            values.addAll(getAns(formElement.getConcept(), codedObs));
        } else {
            values.add(val == null ? "" : getAnsName(formElement.getConcept(), val));
        }
        return values;
    }

    private List<Object> getObservationValueList(Object val) {
        return val == null ?
                Collections.emptyList() :
                val instanceof List ? (List<Object>) val : Collections.singletonList(val);
    }

    private String processMediaObs(Object val) {
        List<String> imageURIs = getObservationValueList(val).stream().map(t -> (String) t).collect(Collectors.toList());
        return getFieldValue(String.join(",", imageURIs));
    }

    private String getFieldValue(String value) {
        return String.format("\"%s\"", value);
    }

    private String getAnsName(Concept concept, Object val) {
        return concept.getSortedAnswers()
                .filter(ca -> ca.getAnswerConcept().getUuid().equals(val))
                .map(ca -> getFieldValue(ca.getAnswerConcept().getName()))
                .findFirst().orElse("");
    }

    private List<String> getAns(Concept concept, List<Object> val) {
        return concept.getSortedAnswers()
                .map(ca -> val.contains(ca.getAnswerConcept().getUuid()) ? selectedAnswerFieldValue : unSelectedAnswerFieldValue)
                .collect(Collectors.toList());
    }

    private void addAddressLevels(List<Object> row, AddressLevel addressLevel) {
        Map<String, String> addressLevelMap = addressLevel != null ?
                getAddressTypeAddressLevelMap(addressLevel, addressLevel.getParentLocationMapping()) : new HashMap<>();
        this.addressLevelTypes.forEach(level -> row.add(QuotedStringValue(addressLevelMap.getOrDefault(level, ""))));
    }

    private Map<String, String> getAddressTypeAddressLevelMap(AddressLevel addressLevel, ParentLocationMapping parentLocationMapping) {
        Map<String, String> addressTypeAddressLevelMap = new HashMap<>();
        addressTypeAddressLevelMap.put(addressLevel.getType().getName(), addressLevel.getTitle());
        if (parentLocationMapping == null) {
            return addressTypeAddressLevelMap;
        }
        AddressLevel parentLocation = parentLocationMapping.getParentLocation();
        while (parentLocation != null) {
            addressTypeAddressLevelMap.put(parentLocation.getType().getName(), parentLocation.getTitle());
            parentLocation = parentLocation.getParentLocation();
        }
        return addressTypeAddressLevelMap;
    }

    private void addBlanks(List<Object> row, long noOfColumns) {
        for (int i = 0; i < noOfColumns; i++) {
            row.add(EMPTY_STRING);
        }
    }

    private String QuotedStringValue(String text) {
        if (StringUtils.isEmpty(text))
            return text;
        return "\"".concat(text).concat("\"");
    }
}
