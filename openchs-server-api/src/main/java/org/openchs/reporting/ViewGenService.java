package org.openchs.reporting;

import org.openchs.application.FormElement;
import org.openchs.application.FormMapping;
import org.openchs.application.FormType;
import org.openchs.dao.OperationalEncounterTypeRepository;
import org.openchs.dao.OperationalProgramRepository;
import org.openchs.dao.OperationalSubjectTypeRepository;
import org.openchs.dao.application.FormElementRepository;
import org.openchs.dao.application.FormMappingRepository;
import org.openchs.domain.*;
import org.openchs.service.ViewNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@Service
public class ViewGenService {
    private final String ENCOUNTER_TEMPLATE;
    private final String ENCOUNTER_CANCEL_TEMPLATE;
    private final String REGISTRATION_TEMPLATE;
    private final String GENERAL_ENCOUNTER_TEMPLATE;
    private final String GENERAL_ENCOUNTER_CANCEL_TEMPLATE;
    private final String PROGRAM_ENROLMENT_TEMPLATE;
    private final String PROGRAM_ENROLMENT_EXIT_TEMPLATE;

    private final String CONCEPT_MAP_TEMPLATE;

    private final OperationalProgramRepository operationalProgramRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    private final FormMappingRepository formMappingRepository;
    private FormElementRepository formElementRepository;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public ViewGenService(OperationalProgramRepository operationalProgramRepository,
                          OperationalEncounterTypeRepository operationalEncounterTypeRepository,
                          OperationalSubjectTypeRepository operationalSubjectTypeRepository,
                          FormMappingRepository formMappingRepository, FormElementRepository formElementRepository) throws IOException {
        this.operationalProgramRepository = operationalProgramRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.formMappingRepository = formMappingRepository;
        this.formElementRepository = formElementRepository;

        ENCOUNTER_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/programEncounter.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        ENCOUNTER_CANCEL_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/programEncounterCancel.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        REGISTRATION_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/registration.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        GENERAL_ENCOUNTER_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/generalEncounter.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        GENERAL_ENCOUNTER_CANCEL_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/generalEncounterCancel.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        PROGRAM_ENROLMENT_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/programEnrolment.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
        PROGRAM_ENROLMENT_EXIT_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/programEnrolmentExit.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));

        CONCEPT_MAP_TEMPLATE = new BufferedReader(new InputStreamReader(new ClassPathResource("/pivot/conceptMap.sql").getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    public Map<String, String> registrationViews(String subjectTypeName, boolean spreadMultiSelectObs) {
        FormMapping formMapping = formMappingRepository.findBySubjectTypeNameAndFormFormTypeAndIsVoidedFalse(subjectTypeName, FormType.IndividualProfile);
        List<FormElement> registrationFormElements = getRegistrationFormElements(getOperationalSubjectType(subjectTypeName).getSubjectType().getId());

        String sql = REGISTRATION_TEMPLATE.replace("${selections}",
                buildObservationSelection("individual", registrationFormElements, spreadMultiSelectObs))
                .replace("${operationalSubjectTypeUuid}", operationalSubjectTypeRepository.findByNameIgnoreCase(subjectTypeName).getUuid());
        return new HashMap<String, String>() {{
            put("Registration", replaceJoinsAndOtherCommonInformation(formMapping, sql));
        }};
    }

    public Map<String, String> enrolmentViews(String subjectTypeName, String operationalProgramName) {
        OperationalSubjectType operationalSubjectType = getOperationalSubjectType(subjectTypeName);
        OperationalProgram operationalProgram = operationalProgramRepository.findByNameIgnoreCase(operationalProgramName);
        if (operationalProgram == null) {
            return new HashMap<>();
        }
        Long subjectTypeId = operationalSubjectType.getSubjectType().getId();
        List<FormElement> enrolmentFormElements = getProgramEnrolmentFormElements(operationalProgram, subjectTypeId);
        FormMapping enrolmentFormMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(operationalProgram.getProgram().getId(), null, FormType.ProgramEnrolment, subjectTypeId);

        HashMap<String, String> map = new HashMap<>();
        String enrolmentSql = replaceSubjectAndEnrolmentObsInTemplate(PROGRAM_ENROLMENT_TEMPLATE, false, operationalSubjectType.getUuid(), enrolmentFormElements, operationalProgram.getUuid());
        enrolmentSql = replaceJoinsAndOtherCommonInformation(enrolmentFormMapping, enrolmentSql);
        map.put(operationalProgramName, enrolmentSql);

        FormMapping exitFormMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(operationalProgram.getProgram().getId(), null, FormType.ProgramExit, subjectTypeId);
        if (exitFormMapping != null) {
            String enrolmentExitSql = replaceSubjectAndEnrolmentObsInTemplate(PROGRAM_ENROLMENT_EXIT_TEMPLATE, false, operationalSubjectType.getUuid(), enrolmentFormElements, operationalProgram.getUuid())
                    .replace("${programEnrolmentExit}", buildExitObservationSelection(getProgramEnrolmentExitFormElements(operationalProgram, subjectTypeId)));
            enrolmentExitSql = replaceJoinsAndOtherCommonInformation(exitFormMapping, enrolmentExitSql);
            map.put(ViewNameGenerator.getExitName(operationalProgramName), enrolmentExitSql);
        }
        return map;
    }

    public Map<String, String> getSqlsFor(String operationalProgramName, String operationalEncounterTypeName, boolean spreadMultiSelectObs, String subjectTypeName) {
        OperationalSubjectType operationalSubjectType = getOperationalSubjectType(subjectTypeName);
        OperationalProgram operationalProgram = operationalProgramRepository.findByNameIgnoreCase(operationalProgramName);
        List<OperationalEncounterType> operationalEncounterTypes = operationalEncounterTypeName == null ?
                operationalEncounterTypeRepository.findAll() :
                singletonList(operationalEncounterTypeRepository.findByNameIgnoreCase(operationalEncounterTypeName));
        if (operationalProgram == null) {
            return getGeneralEncounterSqls(operationalEncounterTypes, spreadMultiSelectObs, operationalSubjectType.getSubjectType().getId(), operationalSubjectType.getUuid());
        } else if (operationalProgram.getProgram() != null) {
            return getProgramEncounterSqls(operationalProgram, operationalEncounterTypes, spreadMultiSelectObs, operationalSubjectType.getSubjectType().getId(), operationalSubjectType.getUuid());
        }
        throw new IllegalArgumentException(String.format("Not found OperationalProgram{name='%s'}", operationalProgramName));
    }

    private Map<String, String> getProgramEncounterSqls(OperationalProgram operationalProgram, List<OperationalEncounterType> operationalEncounterTypes, boolean spreadMultiSelectObs, Long subjectTypeId, String operationalSubjectTypeUUID) {
        List<FormElement> enrolmentFormElements = getProgramEnrolmentFormElements(operationalProgram, subjectTypeId);

        String programEncounterQuery = replaceSubjectAndEnrolmentObsInTemplate(ENCOUNTER_TEMPLATE, spreadMultiSelectObs, operationalSubjectTypeUUID, enrolmentFormElements, operationalProgram.getUuid());
        String programEncounterCancelQuery = replaceSubjectAndEnrolmentObsInTemplate(ENCOUNTER_CANCEL_TEMPLATE, spreadMultiSelectObs, operationalSubjectTypeUUID, enrolmentFormElements, operationalProgram.getUuid());

        Map<String, String> programEncounterSqlMap = new HashMap<>();
        operationalEncounterTypes.stream()
                .filter(operationalEncounterType -> {
                    FormMapping formMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(operationalProgram.getProgram().getId(), operationalEncounterType.getEncounterType().getId(), FormType.ProgramEncounter, subjectTypeId);
                    return formMapping != null;
                })
                .forEach(type -> {
                    FormMapping formMappingForProgramEncounter = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(operationalProgram.getProgram().getId(), type.getEncounterType().getId(), FormType.ProgramEncounter, subjectTypeId);
                    String sqlForProgramEncounter = getSqlForProgramEncounter(programEncounterQuery, type, spreadMultiSelectObs, getProgramEncounterFormElements(operationalProgram, type, subjectTypeId), formMappingForProgramEncounter);

                    FormMapping formMappingForProgramEncounterCancel = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(operationalProgram.getProgram().getId(), type.getEncounterType().getId(), FormType.ProgramEncounterCancellation, subjectTypeId);
                    programEncounterSqlMap.put(type.getName(), sqlForProgramEncounter);

                    if (formMappingForProgramEncounterCancel != null) {
                        String sqlForProgramEncounterCancel = getSqlForProgramEncounterCancel(programEncounterCancelQuery, type, spreadMultiSelectObs, getProgramEncounterCancelFormElements(operationalProgram, type, subjectTypeId), formMappingForProgramEncounterCancel);
                        programEncounterSqlMap.put(ViewNameGenerator.getCancelName(type.getName()), sqlForProgramEncounterCancel);
                    }
                });
        return programEncounterSqlMap;
    }

    private String replaceSubjectAndEnrolmentObsInTemplate(String template, boolean spreadMultiSelectObs, String operationalSubjectTypeUUID, List<FormElement> enrolmentFormElements, String uuid) {
        return template.replace("${operationalProgramUuid}", uuid)
                .replace("${operationalSubjectTypeUuid}", operationalSubjectTypeUUID)
                .replace("${programEnrolment}", buildObservationSelection("programEnrolment", enrolmentFormElements, spreadMultiSelectObs));
    }

    private Map<String, String> getGeneralEncounterSqls(List<OperationalEncounterType> types, boolean spreadMultiSelectObs, Long subjectTypeId, String operationalSubjectTypeUUID) {
        String generalEncounterQuery = replaceSubjectTypeUUID(GENERAL_ENCOUNTER_TEMPLATE, operationalSubjectTypeUUID);
        String generalEncounterCancelQuery = replaceSubjectTypeUUID(GENERAL_ENCOUNTER_CANCEL_TEMPLATE, operationalSubjectTypeUUID);
        Map<String, String> generalEncounterSqlMap = new HashMap<>();
        types.forEach(type -> {
                    FormMapping formMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(null, type.getEncounterType().getId(), FormType.Encounter, subjectTypeId);
                    FormMapping cancelFormMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(null, type.getEncounterType().getId(), FormType.IndividualEncounterCancellation, subjectTypeId);
                    if (formMapping != null) {
                        generalEncounterSqlMap.put(type.getName(), getSqlForGeneralEncounter(generalEncounterQuery, type, spreadMultiSelectObs, getGeneralEncounterFormElements(subjectTypeId, type), formMapping));
                    }
                    if (formMapping != null && cancelFormMapping != null)
                        generalEncounterSqlMap.put(type.getName().concat(" CANCEL"), getSqlForGeneralEncounterCancel(generalEncounterCancelQuery, type, spreadMultiSelectObs, getGeneralEncounterCancelFormElements(subjectTypeId, type), cancelFormMapping));
                });
        return generalEncounterSqlMap;
    }

    private String replaceSubjectTypeUUID(String template, String operationalSubjectTypeUUID) {
        return template.replace("${operationalSubjectTypeUuid}", operationalSubjectTypeUUID);
    }

    private String getSqlForProgramEncounter(String mainViewQuery, OperationalEncounterType operationalEncounterType, boolean spreadMultiSelectObs, List<FormElement> formElements, FormMapping formMapping) {
        String programEncounterSql = mainViewQuery
                .replace("${operationalEncounterTypeUuid}", operationalEncounterType.getUuid())
                .replace("${programEncounter}", buildObservationSelection("programEncounter", formElements, spreadMultiSelectObs));
        return replaceJoinsAndOtherCommonInformation(formMapping, programEncounterSql);
    }

    private String replaceJoinsAndOtherCommonInformation(FormMapping formMapping, String sql) {
        return sql.replace("${concept_maps}", ConceptMapSQLGenerator.generateWithClauses(formMapping, formElementRepository, CONCEPT_MAP_TEMPLATE))
                .replace("${cross_join_concept_maps}", ConceptMapSQLGenerator.generateJoins(formMapping))
                .replaceAll("[,\\s]+FROM", "\nFROM")
                .replaceAll("[,\\s]+,", ",");
    }

    private String getSqlForProgramEncounterCancel(String mainViewQuery, OperationalEncounterType operationalEncounterType, boolean spreadMultiSelectObs, List<FormElement> cancelFormElements, FormMapping formMapping) {
        String programEncounterCancelSql = mainViewQuery
                .replace("${operationalEncounterTypeUuid}", operationalEncounterType.getUuid())
                .replace("${programEncounterCancellation}", buildCancelObservationSelection("programEncounter", cancelFormElements, spreadMultiSelectObs));
        return replaceJoinsAndOtherCommonInformation(formMapping, programEncounterCancelSql);
    }

    private String getSqlForGeneralEncounter(String mainViewQuery, OperationalEncounterType operationalEncounterType, boolean spreadMultiSelectObs, List<FormElement> formElements, FormMapping formMapping) {
        String encounterSQL = mainViewQuery
                .replace("${encounterTypeUuid}", operationalEncounterType.getUuid())
                .replace("${encounter}", buildObservationSelection("encounter", formElements, spreadMultiSelectObs));
        return replaceJoinsAndOtherCommonInformation(formMapping, encounterSQL);
    }

    private String getSqlForGeneralEncounterCancel(String mainViewQuery, OperationalEncounterType operationalEncounterType, boolean spreadMultiSelectObs, List<FormElement> cancelFormElements, FormMapping formMapping) {
        String encounterCancelSql = mainViewQuery
                .replace("${encounterTypeUuid}", operationalEncounterType.getUuid())
                .replace("${encounterCancellation}", buildCancelObservationSelection("encounter", cancelFormElements, spreadMultiSelectObs));
        return replaceJoinsAndOtherCommonInformation(formMapping, encounterCancelSql);
    }

    private List<FormElement> getRegistrationFormElements(Long subjectTypeId) {
        return getFormElements(null, null, FormType.IndividualProfile, subjectTypeId);
    }

    private List<FormElement> getProgramEnrolmentFormElements(OperationalProgram operationalProgram, Long subjectTypeId) {
        return getFormElements(operationalProgram.getProgram().getId(), null, FormType.ProgramEnrolment, subjectTypeId);
    }

    private List<FormElement> getProgramEnrolmentExitFormElements(OperationalProgram operationalProgram, Long subjectTypeId) {
        return getFormElements(operationalProgram.getProgram().getId(), null, FormType.ProgramExit, subjectTypeId);
    }

    private List<FormElement> getGeneralEncounterFormElements(Long subjectTypeId, OperationalEncounterType type) {
        return getFormElements(null, type.getEncounterType().getId(), FormType.Encounter, subjectTypeId);
    }

    private List<FormElement> getGeneralEncounterCancelFormElements(Long subjectTypeId, OperationalEncounterType type) {
        return getFormElements(null, type.getEncounterType().getId(), FormType.IndividualEncounterCancellation, subjectTypeId);
    }

    private List<FormElement> getProgramEncounterFormElements(OperationalProgram operationalProgram, OperationalEncounterType type, Long subjectTypeId) {
        return getFormElements(operationalProgram.getProgram().getId(), type.getEncounterType().getId(), FormType.ProgramEncounter, subjectTypeId);
    }

    private List<FormElement> getProgramEncounterCancelFormElements(OperationalProgram operationalProgram, OperationalEncounterType type, Long subjectTypeId) {
        return getFormElements(operationalProgram.getProgram().getId(), type.getEncounterType().getId(), FormType.ProgramEncounterCancellation, subjectTypeId);
    }

    private List<FormElement> getFormElements(Long programId, Long typeId, FormType formType, Long subjectTypeId) {
        FormMapping formMapping = formMappingRepository.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(programId, typeId, formType, subjectTypeId);
        List<FormElement> formElements = formMapping == null ? Collections.EMPTY_LIST : formMapping.getForm().getApplicableFormElements();
        return formElements.stream().filter(fe -> !FormElement.PLACEHOLDER_CONCEPT_UUID.equals(fe.getConcept().getUuid())).collect(Collectors.toList());
    }

    private String buildObservationSelection(String entity, List<FormElement> elements, boolean spreadMultiSelectObs) {
        return buildObservationSelection(entity, elements, spreadMultiSelectObs, "observations");
    }

    private String buildCancelObservationSelection(String entity, List<FormElement> elements, boolean spreadMultiSelectObs) {
        return buildObservationSelection(entity, elements, spreadMultiSelectObs, "cancel_observations");
    }

    private String buildExitObservationSelection(List<FormElement> elements) {
        return buildObservationSelection("programEnrolment", elements, false, "program_exit_observations");
    }

    private String buildObservationSelection(String entity, List<FormElement> elements, Boolean spreadMultiSelectObs, String obsColumnName) {
        String obsColumn = entity + "." + obsColumnName;
        return elements.parallelStream().map(formElement -> {
            Concept concept = formElement.getConcept();
            String conceptUUID = concept.getUuid();
            String columnName = concept.getViewColumnName();
            switch (ConceptDataType.valueOf(concept.getDataType())) {
                case Coded: {
                    if (spreadMultiSelectObs) {
                        return spreadMultiSelectSQL(obsColumn, concept);
                    }
                    return String.format("public.get_coded_string_value(%s->'%s', concepts_%d.map)::TEXT as \"%s\"",
                            obsColumn, conceptUUID, formElement.getFormElementGroup().getId(), columnName);
                }
                case Date:
                case DateTime: {
                    return String.format("(%s->>'%s')::DATE as \"%s\"", obsColumn, conceptUUID, columnName);
                }
                case Numeric: {
                    return String.format("(%s->>'%s')::NUMERIC as \"%s\"", obsColumn, conceptUUID, columnName);
                }
                default: {
                    return String.format("(%s->>'%s')::TEXT as \"%s\"", obsColumn, conceptUUID, columnName);
                }
            }
        }).collect(Collectors.joining(",\n"));
    }

    private String spreadMultiSelectSQL(String obsColumn, Concept concept) {
        String obsSubColumn = String.format("(%s->'%s')", obsColumn, concept.getUuid());
        return concept.getConceptAnswers().stream().map(ConceptAnswer::getAnswerConcept)
                .map(aConcept -> String.format("boolean_txt(%s ? '%s') as \"%s(%s)\"",
                        obsSubColumn, aConcept.getUuid(), aConcept.getName(), concept.getName()))
                .collect(Collectors.joining(",\n"));
    }

    private OperationalSubjectType getOperationalSubjectType(String subjectTypeName) {
        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findByNameIgnoreCase(subjectTypeName);

        if (operationalSubjectType != null) {
            return operationalSubjectType;
        } else {
            throw new IllegalArgumentException(String.format("Not found operationalSubject{name='%s'}", subjectTypeName));
        }
    }
}
