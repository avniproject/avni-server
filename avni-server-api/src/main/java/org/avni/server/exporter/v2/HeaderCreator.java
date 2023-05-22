package org.avni.server.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.domain.*;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class HeaderCreator implements LongitudinalExportRequestFieldNameConstants, LongitudinalExportDBFieldNameConstants {
    private static Map<String, HeaderNameAndFunctionMapper<Individual>> registrationDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<Individual>>() {{
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

    private static Map<String, HeaderNameAndFunctionMapper<ProgramEnrolment>> enrolmentDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<ProgramEnrolment>>() {{
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


    private static Map<String, HeaderNameAndFunctionMapper<AbstractEncounter>> encounterDataMap = new LinkedHashMap<String, HeaderNameAndFunctionMapper<AbstractEncounter>>() {{
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

    public StringBuilder addRegistrationHeaders(SubjectType subjectType,
                                                Map<String, FormElement> registrationMap,
                                                List<String> addressLevelTypes,
                                                ExportEntityType subjectRegistrationExport) {
        StringBuilder registrationHeaders = new StringBuilder();
        String subjectTypeName = subjectType.getName();
        registrationHeaders.append(getStaticRegistrationHeaders(subjectRegistrationExport, subjectTypeName));
        addAddressLevelHeaderNames(registrationHeaders, addressLevelTypes);
        if (subjectType.isGroup()) {
            registrationHeaders.append(",").append(subjectTypeName).append(".total_members");
        }
        appendObsHeaders(registrationHeaders, subjectTypeName, registrationMap, subjectRegistrationExport);
        return registrationHeaders;
    }

    public StringBuilder addEnrolmentHeaders(Map<String, FormElement> enrolmentMap,
                                             Map<String, FormElement> exitEnrolmentMap,
                                             String programName,
                                             ExportEntityType exportEntityType) {
        StringBuilder enrolmentHeaders = new StringBuilder();
        enrolmentHeaders.append(getStaticEnrolmentHeaders(exportEntityType, programName));
        appendObsHeaders(enrolmentHeaders, programName, enrolmentMap, exportEntityType);
        appendObsHeaders(enrolmentHeaders, programName + "_exit", exitEnrolmentMap, exportEntityType);
        return enrolmentHeaders;
    }

    public StringBuilder addEncounterHeaders(Long maxVisitCount,
                                             Map<String, FormElement> encounterMap,
                                             Map<String, FormElement> encounterCancelMap,
                                             String encounterTypeName,
                                             ExportEntityType exportEntityType) {
        StringBuilder encounterHeaders = new StringBuilder();
        int visit = 0;
        while (visit < maxVisitCount) {
            visit++;
            if (visit != 1) {
                encounterHeaders.append(",");
            }
            String prefix = encounterTypeName + "_" + visit;
            encounterHeaders.append(appendStaticEncounterHeaders(exportEntityType, prefix));
            appendObsHeaders(encounterHeaders, prefix, encounterMap, exportEntityType);
            appendObsHeaders(encounterHeaders, prefix, encounterCancelMap, exportEntityType);
        }
        return encounterHeaders;
    }

    private String getStaticRegistrationHeaders(ExportEntityType exportFields, String prefix) {
        init(exportFields, registrationDataMap);
        return exportFields.getOutputFields().stream()
                .filter(registrationDataMap::containsKey)
                .map(key -> format("%s_%s", prefix, registrationDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private String getStaticEnrolmentHeaders(ExportEntityType exportEntityType, String prefix) {
        init(exportEntityType, enrolmentDataMap);
        return exportEntityType.getOutputFields().stream()
                .filter(enrolmentDataMap::containsKey)
                .map(key -> format("%s_%s", prefix, enrolmentDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private String appendStaticEncounterHeaders(ExportEntityType exportFields, String prefix) {
        init(exportFields, encounterDataMap);
        return exportFields.getOutputFields().stream()
                .filter(encounterDataMap::containsKey)
                .map(key -> format("%s_%s", prefix, encounterDataMap.get(key).getName()))
                .collect(Collectors.joining(","));
    }

    private void init(ExportEntityType exportEntityType, Map<String, ?> map) {
        if (exportEntityType.hasUserProvidedFields()) {
            exportEntityType.addAllUserProvidedFields();
        } else {
            exportEntityType.addFields(map.keySet());
        }
    }

    private void addAddressLevelHeaderNames(StringBuilder sb, List<String> addressLevelTypes) {
        addressLevelTypes.forEach(level -> sb.append(",").append(quotedStringValue(level)));
    }

    protected String quotedStringValue(String text) {
        if (StringUtils.isEmpty(text))
            return text;
        return "\"".concat(text).concat("\"");
    }

    private void appendObsHeaders(StringBuilder sb, String prefix, Map<String, FormElement> map, ExportEntityType exportEntityType) {
        map.forEach((uuid, fe) -> {
            Concept concept = fe.getConcept();
            String groupPrefix = fe.getGroup() == null ? "" : (fe.getGroup().getConcept().getName() + "_");
            if (ConceptDataType.isGroupQuestion(concept.getDataType())) {
                return;
            }

            if (fe.isCodedMultiSelect()) {
                if (exportEntityType.hasUserProvided(concept))
                    exportEntityType.removeField(concept);

                concept.getSortedAnswers().forEach(ca -> {
                    sb.append(",\"")
                            .append(prefix)
                            .append("_")
                            .append(groupPrefix)
                            .append(concept.getName())
                            .append("_").append(ca.getAnswerConcept().getName()).append("\"");
                    if (!exportEntityType.hasUserProvidedFields()) {
                        exportEntityType.addField(ca);
                    }
                });
            } else {
                if (exportEntityType.hasUserProvided(concept))
                    exportEntityType.addField(concept);

                sb.append(",\"").append(prefix).append("_").append(groupPrefix).append(concept.getName()).append("\"");
            }
        });
    }

    public static Map<String, HeaderNameAndFunctionMapper<Individual>> getRegistrationDataMap() {
        return registrationDataMap;
    }

    public static Map<String, HeaderNameAndFunctionMapper<ProgramEnrolment>> getEnrolmentDataMap() {
        return enrolmentDataMap;
    }

    public static Map<String, HeaderNameAndFunctionMapper<AbstractEncounter>> getEncounterDataMap() {
        return encounterDataMap;
    }
}
