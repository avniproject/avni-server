package org.avni.server.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportEntityTypeVisitor;

import java.util.*;
import java.util.stream.Collectors;

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

    public HeaderCreator(SubjectTypeRepository subjectTypeRepository, List<String> addressLevelTypes, Map<FormElement, Integer> maxRepeatableQuestionGroupObservation, EncounterTypeRepository encounterTypeRepository, ExportFieldsManager exportFieldsManager, ProgramRepository programRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
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
            headerBuilder.append(getStaticEncounterHeaders(exportEntityType, encounterType, visit));
            appendForm(encounterType.getName(), visit, exportFieldsManager.getMainFields(exportEntityType), maxRepeatableQuestionGroupObservation);
            appendForm(encounterType.getName(), visit, exportFieldsManager.getSecondaryFields(exportEntityType), maxRepeatableQuestionGroupObservation);
        }
    }

    @Override
    public void visitEncounter(ExportEntityType encounter, ExportEntityType subjectTypeExportEntityType) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounter.getUuid());
        this.addEncounterHeaders(exportFieldsManager.getMaxEntityCount(encounter), encounterType, encounter, maxRepeatableQuestionGroupObservation);
    }

    private String getStaticRegistrationHeaders(ExportEntityType subject, SubjectType subjectType) {
        return exportFieldsManager.getCoreFields(subject).stream()
                .filter(registrationDataMap::containsKey)
                .map(key -> this.getFieldHeader(registrationDataMap.get(key).getName(), null, subjectType.getName(), null, null, false))
                .collect(Collectors.joining(""));
    }

    private String getStaticEnrolmentHeaders(ExportEntityType exportEntityType, Program program) {
        return exportFieldsManager.getCoreFields(exportEntityType).stream()
                .filter(enrolmentDataMap::containsKey)
                .map(key -> this.getFieldHeader(enrolmentDataMap.get(key).getName(), null, program.getName(), null, null, false))
                .collect(Collectors.joining(""));
    }

    private String getStaticEncounterHeaders(ExportEntityType exportEntityType, EncounterType encounterType, Integer encounterIndex) {
        return exportFieldsManager.getCoreFields(exportEntityType).stream()
                .filter(encounterDataMap::containsKey)
                .map(key -> this.getFieldHeader(encounterDataMap.get(key).getName(), null, encounterType.getName(), encounterIndex, null, false))
                .collect(Collectors.joining(""));
    }

    private String getAddressLevelHeaders(List<String> addressLevelTypes, SubjectType subjectType) {
        return addressLevelTypes.stream().map(s -> this.getFieldHeader(s, null, subjectType.getName(), null, null, true)).collect(Collectors.joining(""));
    }

    private void appendForm(String entityType, Integer entityTypeIndex, Map<String, FormElement> formElements, Map<FormElement, Integer> maxNumberOfQuestionGroupObservations) {
        //handle all non-repeated observations (include question group observation with only max one set)
        formElements.forEach((uuid, fe) -> {
            if (fe.isPartOfRepeatableQuestionGroup() || fe.isQuestionGroupElement()) return;
            appendFormElement(fe, entityType, entityTypeIndex, null);
        });

        //handle all repeated question group observations
        List<FormElement> observationsRepeatedMultipleTimes = formElements.values().stream()
                .filter(FormElement::isPartOfRepeatableQuestionGroup)
                .collect(Collectors.toList());
        Map<FormElement, List<FormElement>> repeatedFormElements = ExportFieldsManager.groupByQuestionGroup(observationsRepeatedMultipleTimes);
        repeatedFormElements.forEach((qgFormElement, qgFormElements) -> {
            Integer maxRepeats = maxNumberOfQuestionGroupObservations.get(qgFormElement);
            for (int i = 1; i <= maxRepeats; i++) {
                for (FormElement formElement : qgFormElements) {
                    appendFormElement(formElement, entityType, entityTypeIndex, i);
                }
            }
        });
    }

    private void appendFormElement(FormElement formElement, String entityType, Integer encounterIndex, Integer repeatableQGIndex) {
        FormElement group = formElement.getGroup();
        String groupName = group == null ? null : group.getConcept().getName();
        Concept concept = formElement.getConcept();
        if (formElement.isCodedMultiSelect()) {
            concept.getSortedAnswers().forEach(ca -> {
                String fieldName = concept.getName() + "_" + ca.getAnswerConcept().getName();
                headerBuilder.append(getFieldHeader(fieldName, groupName, entityType, encounterIndex, repeatableQGIndex, true));
            });
        } else {
            headerBuilder.append(getFieldHeader(concept.getName(), groupName, entityType, encounterIndex, repeatableQGIndex, true));
        }
    }

    private String getFieldHeader(String fieldName, String fieldGroup, String entityType, Integer entityTypeIndex, Integer repeatableQGIndex, boolean mayContainComma) {
        StringBuilder fieldHeaderBuilder = new StringBuilder();
        if (mayContainComma) fieldHeaderBuilder.append("\"");

        fieldHeaderBuilder.append(entityType).append("_");
        if (entityTypeIndex != null) fieldHeaderBuilder.append(entityTypeIndex).append("_");
        if (fieldGroup != null) fieldHeaderBuilder.append(fieldGroup).append("_");
        if (repeatableQGIndex != null) fieldHeaderBuilder.append(repeatableQGIndex).append("_");
        fieldHeaderBuilder.append(fieldName);

        if (mayContainComma) fieldHeaderBuilder.append("\"");
        fieldHeaderBuilder.append(",");
        return fieldHeaderBuilder.toString();
    }

    @Override
    public void visitSubject(ExportEntityType subject) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subject.getUuid());

        headerBuilder.append(getStaticRegistrationHeaders(subject, subjectType));
        headerBuilder.append(getAddressLevelHeaders(addressLevelTypes, subjectType));

        if (subjectType.isGroup()) {
            this.getFieldHeader("total_members", null, subjectType.getName(), null, null, false);
        }

        appendForm(subjectType.getName(), null, exportFieldsManager.getMainFields(subject), maxRepeatableQuestionGroupObservation);
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
        appendForm(program.getName(), null, exportFieldsManager.getMainFields(programExportEntityType), maxRepeatableQuestionGroupObservation);
        appendForm(program.getName() + "_exit", null, exportFieldsManager.getSecondaryFields(programExportEntityType), maxRepeatableQuestionGroupObservation);
    }

    @Override
    public void visitProgramEncounter(ExportEntityType exportEntityType, ExportEntityType programExportEntityType, ExportEntityType subject) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(exportEntityType.getUuid());
        this.addEncounterHeaders(exportFieldsManager.getMaxEntityCount(exportEntityType), encounterType, exportEntityType, maxRepeatableQuestionGroupObservation);
    }

    public String getHeader() {
        return headerBuilder.toString();
    }
}
