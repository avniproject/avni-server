package org.avni.server.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.service.FormMappingService;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportEntityTypeVisitor;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class HeaderCreator implements LongitudinalExportRequestFieldNameConstants, LongitudinalExportDBFieldNameConstants, ExportEntityTypeVisitor {
    public static Map<String, HeaderNameAndFunctionMapper<Individual>> registrationDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<Individual>>() {{
        put(ID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_ID, CHSBaseEntity::getId));
        put(UUID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_UUID, CHSBaseEntity::getUuid));
        put(FIRST_NAME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_FIRST_NAME, Individual::getFirstName));
        put(MIDDLE_NAME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_MIDDLE_NAME, (Individual individual) -> {
            if (individual.getSubjectType().isAllowMiddleName()) {
                return individual.getMiddleName();
            } else {
                return "";
            }
        }));
        put(LAST_NAME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_NAME, Individual::getLastName));
        put(DATE_OF_BIRTH, new HeaderNameAndFunctionMapper<>(HEADER_NAME_DATE_OF_BIRTH, Individual::getDateOfBirth));
        put(REGISTRATION_DATE, new HeaderNameAndFunctionMapper<>(HEADER_NAME_REGISTRATION_DATE, Individual::getRegistrationDate));
        put(GENDER, new HeaderNameAndFunctionMapper<>(HEADER_NAME_GENDER, Individual::getGenderName));
        put(CREATED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_BY, (Individual individual) -> individual.getCreatedBy().getName()));
        put(CREATED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_DATE_TIME, Individual::getCreatedDateTime));
        put(LAST_MODIFIED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_BY, (Individual individual) -> individual.getLastModifiedBy().getName()));
        put(LAST_MODIFIED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_DATE_TIME, Individual::getLastModifiedDateTime));
        put(VOIDED, new HeaderNameAndFunctionMapper<>(HEADER_NAME_VOIDED, CHSEntity::isVoided));
    }};

    public static Map<String, HeaderNameAndFunctionMapper<ProgramEnrolment>> enrolmentDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<ProgramEnrolment>>() {{
        put(ID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_ID, CHSBaseEntity::getId));
        put(UUID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_UUID, CHSBaseEntity::getUuid));
        put(ENROLMENT_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_ENROLMENT_DATE_TIME, ProgramEnrolment::getEnrolmentDateTime));
        put(PROGRAM_EXIT_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_PROGRAM_EXIT_DATE_TIME, ProgramEnrolment::getProgramExitDateTime));
        put(CREATED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_BY, (ProgramEnrolment individual) -> individual.getCreatedBy().getName()));
        put(CREATED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_DATE_TIME, ProgramEnrolment::getCreatedDateTime));
        put(LAST_MODIFIED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_BY, (ProgramEnrolment individual) -> individual.getLastModifiedBy().getName()));
        put(LAST_MODIFIED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_DATE_TIME, ProgramEnrolment::getLastModifiedDateTime));
        put(VOIDED, new HeaderNameAndFunctionMapper<>(HEADER_NAME_VOIDED, CHSEntity::isVoided));
    }};


    public static Map<String, HeaderNameAndFunctionMapper<AbstractEncounter>> encounterDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<AbstractEncounter>>() {{
        put(ID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_ID, CHSBaseEntity::getId));
        put(UUID, new HeaderNameAndFunctionMapper<>(HEADER_NAME_UUID, CHSBaseEntity::getUuid));
        put(NAME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_NAME, AbstractEncounter::getName));
        put(EARLIEST_VISIT_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_EARLIEST_VISIT_DATE_TIME, AbstractEncounter::getEarliestVisitDateTime));
        put(MAX_VISIT_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_MAX_VISIT_DATE_TIME, AbstractEncounter::getMaxVisitDateTime));
        put(ENCOUNTER_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_ENCOUNTER_DATE_TIME, AbstractEncounter::getEncounterDateTime));
        put(CANCEL_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CANCEL_DATE_TIME, AbstractEncounter::getCancelDateTime));
        put(CREATED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_BY, (AbstractEncounter individual) -> individual.getCreatedBy().getName()));
        put(CREATED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_CREATED_DATE_TIME, AbstractEncounter::getCreatedDateTime));
        put(LAST_MODIFIED_BY, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_BY, (AbstractEncounter individual) -> individual.getLastModifiedBy().getName()));
        put(LAST_MODIFIED_DATE_TIME, new HeaderNameAndFunctionMapper<>(HEADER_NAME_LAST_MODIFIED_DATE_TIME, AbstractEncounter::getLastModifiedDateTime));
        put(VOIDED, new HeaderNameAndFunctionMapper<>(HEADER_NAME_VOIDED, CHSEntity::isVoided));
    }};
    private final SubjectTypeRepository subjectTypeRepository;
    private final FormMappingService formMappingService;
    private final List<String> addressLevelTypes;
    private final Map<FormElement, Integer> maxRepeatableQuestionGroupObservation;
    private final StringBuilder headerBuilder;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ExportFieldsManager exportFieldsManager;
    private final ProgramRepository programRepository;

    public static Set<String> getRegistrationCoreFields() {
        return registrationDataMap.keySet();
    }

    public static Set<String> getEncounterCoreFields() {
        return encounterDataMap.keySet();
    }

    public static Set<String> getProgramEnrolmentCoreFields() {
        return enrolmentDataMap.keySet();
    }

    public HeaderCreator(SubjectTypeRepository subjectTypeRepository, FormMappingService formMappingService, List<String> addressLevelTypes, Map<FormElement, Integer> maxRepeatableQuestionGroupObservation, EncounterTypeRepository encounterTypeRepository, ExportFieldsManager exportFieldsManager, ProgramRepository programRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.formMappingService = formMappingService;
        this.addressLevelTypes = addressLevelTypes;
        this.maxRepeatableQuestionGroupObservation = maxRepeatableQuestionGroupObservation;
        this.encounterTypeRepository = encounterTypeRepository;
        this.exportFieldsManager = exportFieldsManager;
        this.programRepository = programRepository;
        headerBuilder = new StringBuilder();
    }

    private void addEncounterHeaders(long maxVisitCount,
                                     EncounterType encounterType,
                                     ExportEntityType exportEntityType,
                                     Map<FormElement, Integer> maxRepeatableQuestionGroupObservation) {
        int visit = 0;
        while (visit < maxVisitCount) {
            visit++;
            if (visit != 1) {
                headerBuilder.append(",");
            }
            String prefix = encounterType.getName() + "_" + visit;
            headerBuilder.append(getStaticEncounterHeaders(exportEntityType, prefix));
            appendObsHeaders(prefix, exportFieldsManager.getMainFields(exportEntityType), maxRepeatableQuestionGroupObservation);
            appendObsHeaders(prefix, exportFieldsManager.getSecondaryFields(exportEntityType), maxRepeatableQuestionGroupObservation);
        }
    }

    @Override
    public void visitEncounter(ExportEntityType encounter, ExportEntityType subjectTypeExportEntityType) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounter.getUuid());
        headerBuilder.append(",");
        this.addEncounterHeaders(exportFieldsManager.getMaxEntityCount(encounter), encounterType, encounter, maxRepeatableQuestionGroupObservation);
    }

    private String getStaticRegistrationHeaders(ExportEntityType subject, String subjectTypeName) {
        return exportFieldsManager.getCoreFields(subject).stream()
                .filter(registrationDataMap::containsKey)
                .map(key -> format("%s_%s", subjectTypeName, registrationDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private String getStaticEnrolmentHeaders(ExportEntityType exportEntityType, Program program) {
        return exportFieldsManager.getCoreFields(exportEntityType).stream()
                .filter(enrolmentDataMap::containsKey)
                .map(key -> format("%s_%s", program.getName(), enrolmentDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private String getStaticEncounterHeaders(ExportEntityType exportEntityType, String prefix) {
        return exportFieldsManager.getCoreFields(exportEntityType).stream()
                .filter(encounterDataMap::containsKey)
                .map(key -> format("%s_%s", prefix, encounterDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private void addAddressLevelHeaderNames(List<String> addressLevelTypes) {
        addressLevelTypes.forEach(level -> headerBuilder.append(",").append(quotedStringValue(level)));
    }

    protected String quotedStringValue(String text) {
        if (StringUtils.isEmpty(text))
            return text;
        return "\"".concat(text).concat("\"");
    }

    private void appendObsHeaders(String prefix, Map<String, FormElement> map, Map<FormElement, Integer> maxNumberOfQuestionGroupObservations) {
        //handle all non-repeated observations (include question group observation with only max one set)
        map.forEach((uuid, fe) -> {
            Integer maxRepeats = maxNumberOfQuestionGroupObservations.get(fe);
            if ((fe.isPartOfRepeatableQuestionGroup() && maxRepeats > 1) || fe.isQuestionGroupElement()) return;

            boolean codedMultiSelect = fe.isCodedMultiSelect();
            headerBuilder.append(",\"").append(prefix).append("_");
            // Prefixes
            // no prefix for self
            if (fe.getGroup() != null) {
                headerBuilder.append(fe.getGroup().getConcept().getName()).append("_");
            }
            Concept concept = fe.getConcept();
            if (codedMultiSelect) {
                concept.getSortedAnswers().forEach(ca -> {
                    headerBuilder
                            .append(concept.getName())
                            .append("_").append(ca.getAnswerConcept().getName());
                });
            } else {
                headerBuilder.append(concept.getName());
            }
            headerBuilder.append("\"");
        });

        //handle all repeated question group observations
        List<FormElement> observationsRepeatedMultipleTimes = map.values().stream()
                .filter(FormElement::isPartOfRepeatableQuestionGroup)
                .filter(formElement -> maxNumberOfQuestionGroupObservations.get(formElement.getGroup()) > 1).collect(Collectors.toList());
        Map<FormElement, List<FormElement>> repeatedFormElements = ExportFieldsManager.groupByQuestionGroup(observationsRepeatedMultipleTimes);
        repeatedFormElements.forEach((group, formElements) -> {
            Integer maxRepeats = maxNumberOfQuestionGroupObservations.get(group);
            for (int i = 1; i <= maxRepeats; i++) {
                for (FormElement formElement : formElements) {
                    boolean codedMultiSelect = formElement.isCodedMultiSelect();

                    Concept concept = formElement.getConcept();
                    headerBuilder.append(",\"").append(prefix).append("_");
                    headerBuilder.append(formElement.getGroup().getConcept().getName()).append("_").append(i).append("_");
                    if (codedMultiSelect) {
                        concept.getSortedAnswers().forEach(ca -> {
                            headerBuilder
                                    .append(concept.getName())
                                    .append("_").append(ca.getAnswerConcept().getName());
                        });
                    } else {
                        headerBuilder.append(concept.getName());
                    }
                    headerBuilder.append("\"");
                }
            }
        });
    }

    @Override
    public void visitSubject(ExportEntityType subject) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subject.getUuid());

        headerBuilder.append(getStaticRegistrationHeaders(subject, subjectType.getName()));
        addAddressLevelHeaderNames(addressLevelTypes);
        if (subjectType.isGroup()) {
            headerBuilder.append(",").append(subjectType.getName()).append(".total_members");
        }
        appendObsHeaders(subjectType.getName(), exportFieldsManager.getMainFields(subject), maxRepeatableQuestionGroupObservation);
    }

    @Override
    public void visitGroup(ExportEntityType groupSubject) {
        this.visitSubject(groupSubject);
    }

    @Override
    public void visitGroupEncounter(ExportEntityType groupEncounter, ExportEntityType groupSubject) {
        this.visitEncounter(groupEncounter, groupSubject);
    }

    @Override
    public void visitProgram(ExportEntityType programExportEntityType, ExportEntityType subject) {
        Program program = programRepository.findByUuid(programExportEntityType.getUuid());
        headerBuilder.append(getStaticEnrolmentHeaders(programExportEntityType, program));
        appendObsHeaders(program.getName(), exportFieldsManager.getMainFields(programExportEntityType), maxRepeatableQuestionGroupObservation);
        appendObsHeaders(program.getName() + "_exit", exportFieldsManager.getSecondaryFields(programExportEntityType), maxRepeatableQuestionGroupObservation);
    }

    @Override
    public void visitProgramEncounter(ExportEntityType exportEntityType, ExportEntityType programExportEntityType, ExportEntityType subject) {
        headerBuilder.append(",");
        EncounterType encounterType = encounterTypeRepository.findByUuid(programExportEntityType.getUuid());
        this.addEncounterHeaders(exportFieldsManager.getMaxEntityCount(exportEntityType), encounterType, exportEntityType, maxRepeatableQuestionGroupObservation);
    }

    public String getHeader() {
        return headerBuilder.toString();
    }
}
