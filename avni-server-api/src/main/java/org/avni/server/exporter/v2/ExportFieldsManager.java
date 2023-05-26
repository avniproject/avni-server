package org.avni.server.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.service.FormMappingService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportEntityTypeVisitor;
import org.avni.server.web.external.request.export.ExportFilters;

import java.util.*;
import java.util.stream.Collectors;

public class ExportFieldsManager implements ExportEntityTypeVisitor {
    private final Map<String, Map<String, FormElement>> mainFormMap = new LinkedHashMap<>();
    private final Map<String, Map<String, FormElement>> secondaryFormMap = new LinkedHashMap<>();
    private final Map<String, List<String>> coreFields = new LinkedHashMap<>();
    private final Map<String, Long> maxCounts = new HashMap<>();

    private final FormMappingService formMappingService;
    private final EncounterRepository encounterRepository;
    private final String timeZone;

    public ExportFieldsManager(FormMappingService formMappingService, EncounterRepository encounterRepository, String timeZone) {
        this.formMappingService = formMappingService;
        this.encounterRepository = encounterRepository;
        this.timeZone = timeZone;
    }

    private void setCoreFields(Set<String> allCoreFields, ExportEntityType exportEntityType) {
        HashSet<String> providedFields = new HashSet<>(allCoreFields);
        List<String> fields = exportEntityType.getFields();
        if (fields.size() > 0) {
            providedFields.retainAll(fields);
        }
        coreFields.put(exportEntityType.getUuid(), new ArrayList<>(providedFields));
    }

    private Map<String, FormElement> getObsFields(ExportEntityType exportEntityType, Map<String, FormElement> allFormElementsAndDecisions, Set<String> allCoreFields) {
        HashSet<String> providedFields = new HashSet<>(exportEntityType.getFields());
        providedFields.removeAll(allCoreFields);
        if (providedFields.size() > 0) {
            return allFormElementsAndDecisions.entrySet()
                    .stream()
                    .filter(uuidFormElementEntry -> providedFields.contains(uuidFormElementEntry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new));

        } else {
            return allFormElementsAndDecisions;
        }
    }

    @Override
    public void visitSubject(ExportEntityType exportEntityType) {
        this.setCoreFields(HeaderCreator.getRegistrationCoreFields(), exportEntityType);
        LinkedHashMap<String, FormElement> allFormElementsAndDecisionMap = formMappingService.getAllFormElementsAndDecisionMap(exportEntityType.getUuid(), null, null, FormType.IndividualProfile);
        mainFormMap.put(exportEntityType.getUuid(), getObsFields(exportEntityType, allFormElementsAndDecisionMap, HeaderCreator.registrationDataMap.keySet()));
    }

    @Override
    public void visitEncounter(ExportEntityType encounter, ExportEntityType subjectExportEntityType) {
        processEncounter(encounter, subjectExportEntityType);
    }

    private void processEncounter(ExportEntityType encounter, ExportEntityType subjectExportEntityType) {
        this.setCoreFields(HeaderCreator.getEncounterCoreFields(), encounter);
        LinkedHashMap<String, FormElement> encounterFormElements = formMappingService.getAllFormElementsAndDecisionMap(subjectExportEntityType.getUuid(), null, encounter.getUuid(), FormType.Encounter);
        mainFormMap.put(encounter.getUuid(), this.getObsFields(encounter, encounterFormElements, HeaderCreator.getEncounterCoreFields()));

        LinkedHashMap<String, FormElement> encounterCancelFormElements = formMappingService.getAllFormElementsAndDecisionMap(subjectExportEntityType.getUuid(), null, encounter.getUuid(), FormType.IndividualEncounterCancellation);
        secondaryFormMap.put(encounter.getUuid(), this.getObsFields(encounter, encounterCancelFormElements, HeaderCreator.getEncounterCoreFields()));

        ExportFilters.DateFilter dateFilter = encounter.getFilters().getDate();
        Long maxEncounterCount = encounterRepository.getMaxEncounterCount(encounter.getUuid(), DateTimeUtil.getCalendarTime(dateFilter.getFrom(), timeZone), DateTimeUtil.getCalendarTime(dateFilter.getTo(), timeZone));
        maxEncounterCount = maxEncounterCount == null ? 1 : maxEncounterCount;
        maxCounts.put(encounter.getUuid(), maxEncounterCount);
    }

    @Override
    public void visitGroup(ExportEntityType exportEntityType) {
        this.setCoreFields(HeaderCreator.getRegistrationCoreFields(), exportEntityType);
        LinkedHashMap<String, FormElement> allFormElementsAndDecisionMap = formMappingService.getAllFormElementsAndDecisionMap(exportEntityType.getUuid(), null, null, FormType.IndividualProfile);
        mainFormMap.put(exportEntityType.getUuid(), getObsFields(exportEntityType, allFormElementsAndDecisionMap, HeaderCreator.registrationDataMap.keySet()));
    }

    @Override
    public void visitGroupEncounter(ExportEntityType groupEncounter, ExportEntityType group) {
        processEncounter(groupEncounter, group);
    }

    @Override
    public void visitProgram(ExportEntityType program, ExportEntityType subjectExportEntityType) {
        this.setCoreFields(HeaderCreator.getProgramEnrolmentCoreFields(), program);
        LinkedHashMap<String, FormElement> enrolmentElements = formMappingService.getAllFormElementsAndDecisionMap(subjectExportEntityType.getUuid(), program.getUuid(), null, FormType.ProgramEnrolment);
        mainFormMap.put(program.getUuid(), enrolmentElements);

        LinkedHashMap<String, FormElement> enrolmentExitElements = formMappingService.getAllFormElementsAndDecisionMap(subjectExportEntityType.getUuid(), program.getUuid(), null, FormType.ProgramExit);
        secondaryFormMap.put(program.getUuid(), enrolmentExitElements);
    }

    @Override
    public void visitProgramEncounter(ExportEntityType encounterType, ExportEntityType program, ExportEntityType subject) {
        this.setCoreFields(HeaderCreator.getEncounterCoreFields(), encounterType);
        LinkedHashMap<String, FormElement> encounterFormElements = formMappingService.getAllFormElementsAndDecisionMap(subject.getUuid(), program.getUuid(), encounterType.getUuid(), FormType.ProgramEncounter);
        mainFormMap.put(encounterType.getUuid(), this.getObsFields(encounterType, encounterFormElements, HeaderCreator.getEncounterCoreFields()));

        LinkedHashMap<String, FormElement> encounterCancelFormElements = formMappingService.getAllFormElementsAndDecisionMap(subject.getUuid(), program.getUuid(), encounterType.getUuid(), FormType.ProgramEncounterCancellation);
        secondaryFormMap.put(encounterType.getUuid(), this.getObsFields(encounterType, encounterCancelFormElements, HeaderCreator.getEncounterCoreFields()));
    }

    public long getMaxEntityCount(ExportEntityType exportEntityType) {
        Long aLong = maxCounts.get(exportEntityType.getUuid());
        if (aLong == null) return 1;
        return aLong;
    }

    public Map<String, FormElement> getMainFields(ExportEntityType exportEntityType) {
        return mainFormMap.get(exportEntityType.getUuid());
    }

    public Map<String, FormElement> getSecondaryFields(ExportEntityType exportEntityType) {
        return secondaryFormMap.get(exportEntityType.getUuid());
    }

    public List<String> getCoreFields(ExportEntityType exportEntityType) {
        return coreFields.get(exportEntityType.getUuid());
    }

    private long calculateTotalNumberOfColumns(ExportEntityType exportEntityType, Map<String, Map<String, FormElement>> formMap) {
        return coreFields.get(exportEntityType.getUuid()).size() + mainFormMap.get(exportEntityType.getUuid()).size();
    }

    private long getTotalNumberOfMainColumns(ExportEntityType exportEntityType) {
        return exportEntityType.getAllExportEntityTypes()
                .stream().mapToLong(value -> this.calculateTotalNumberOfColumns(value, mainFormMap)).sum();
    }

    private long getTotalNumberOfSecondaryColumns(ExportEntityType exportEntityType) {
        return exportEntityType.getAllExportEntityTypes()
                .stream().mapToLong(value -> this.calculateTotalNumberOfColumns(value, secondaryFormMap)).sum();
    }

    public long getTotalNumberOfColumns(ExportEntityType exportEntityType) {
        return getTotalNumberOfColumnsPerEntity(exportEntityType) * getMaxEntityCount(exportEntityType);
    }

    public long getTotalNumberOfColumnsPerEntity(ExportEntityType exportEntityType) {
        return (getTotalNumberOfMainColumns(exportEntityType) + getTotalNumberOfSecondaryColumns(exportEntityType));
    }
}
