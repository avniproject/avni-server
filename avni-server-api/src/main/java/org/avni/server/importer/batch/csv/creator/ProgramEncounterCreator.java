package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeaderStrategyFactory;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ProgramEncounterService;
import org.avni.server.service.UserService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEncounterCreator {
    private final ProgramEncounterRepository programEncounterRepository;
    private final ProgramEnrolmentCreator programEnrolmentCreator;
    private final BasicEncounterCreator basicEncounterCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationCreator observationCreator;
    private final ProgramEncounterService programEncounterService;
    private final EncounterHeaderStrategyFactory strategyFactory;
    private final UserService userService;

    @Autowired
    public ProgramEncounterCreator(ProgramEncounterRepository programEncounterRepository,
                                   ProgramEnrolmentCreator programEnrolmentCreator,
                                   BasicEncounterCreator basicEncounterCreator,
                                   FormMappingRepository formMappingRepository,
                                   ObservationCreator observationCreator,
                                   ProgramEncounterService programEncounterService,
                                   EncounterHeaderStrategyFactory strategyFactory,
                                   UserService userService) {
        this.programEncounterRepository = programEncounterRepository;
        this.programEnrolmentCreator = programEnrolmentCreator;
        this.basicEncounterCreator = basicEncounterCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.programEncounterService = programEncounterService;
        this.strategyFactory = strategyFactory;
        this.userService = userService;
    }

    public void create(Row row, String encounterUploadMode) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        String legacyId = row.get(EncounterHeadersCreator.ID);
        if (legacyId != null && !legacyId.trim().isEmpty()) {
            ProgramEncounter existingEncounter = programEncounterRepository.findByLegacyIdOrUuid(legacyId);
            if (existingEncounter != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", legacyId));
            }
        }
        boolean isScheduledVisit = EncounterUploadMode.isScheduleVisitMode(encounterUploadMode);
        LocalDate encounterDate = null;
        EncounterUploadMode mode = isScheduledVisit ? EncounterUploadMode.SCHEDULE_VISIT : EncounterUploadMode.UPLOAD_VISIT_DETAILS;

        ProgramEncounter programEncounter = getOrCreateProgramEncounter(row);
        basicEncounterCreator.updateEncounter(row, programEncounter, allErrorMsgs, mode);

        String enrolmentId = row.get(EncounterHeadersCreator.PROGRAM_ENROLMENT_ID);
        ProgramEnrolment programEnrolment = programEnrolmentCreator.getProgramEnrolment(enrolmentId, EncounterHeadersCreator.PROGRAM_ENROLMENT_ID);
        if (programEnrolment == null) {
            ValidationUtil.handleErrors(allErrorMsgs);
            return;
        }
        programEncounter.setProgramEnrolment(programEnrolment);

        if (isScheduledVisit) {
            String earliestDateStr = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE);
            String maxDateStr = row.get(EncounterHeadersCreator.MAX_VISIT_DATE);
            if (earliestDateStr == null || earliestDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
            } else {
                LocalDate earliestDate = DateTimeUtil.parseFlexibleDate(earliestDateStr);
                if (earliestDate != null && earliestDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
                }
                if (earliestDate != null) {
                    LocalDate enrolmentDate = programEnrolment.getEnrolmentDateTime().toLocalDate();
                    if (earliestDate.isBefore(enrolmentDate)) {
                        allErrorMsgs.add("Earliest visit date needs to be after program enrolment date");
                    }
                }
                programEncounter.setEarliestVisitDateTime(earliestDate != null ? earliestDate.toDateTimeAtStartOfDay() : null);
            }
            if (maxDateStr == null || maxDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.MAX_VISIT_DATE));
            } else {
                LocalDate maxDate = DateTimeUtil.parseFlexibleDate(maxDateStr);
                if (maxDate != null && maxDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.MAX_VISIT_DATE));
                }
                programEncounter.setMaxVisitDateTime(maxDate != null ? maxDate.toDateTimeAtStartOfDay() : null);
            }
        } else {
            String encounterDateStr = row.get(EncounterHeadersCreator.VISIT_DATE);
            if (encounterDateStr == null || encounterDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            } else {
                encounterDate = DateTimeUtil.parseFlexibleDate(encounterDateStr);
                if (encounterDate == null) {
                    allErrorMsgs.add(String.format("Invalid date format for '%s'. Expected format: DD-MM-YYYY or YYYY-MM-DD", EncounterHeadersCreator.VISIT_DATE));
                }
                if (encounterDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.VISIT_DATE));
                }
                if (encounterDate != null) {
                    LocalDate enrolmentDate = programEnrolment.getEnrolmentDateTime().toLocalDate();
                    if (encounterDate.isBefore(enrolmentDate)) {
                        allErrorMsgs.add("Visit date needs to be after program enrolment date");
                    }
                }
                programEncounter.setEncounterDateTime(encounterDate != null ? encounterDate.toDateTimeAtStartOfDay() : null, userService.getCurrentUser());
            }
        }

        ValidationUtil.handleErrors(allErrorMsgs);
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(programEnrolment.getIndividual().getSubjectType().getUuid(), programEnrolment.getProgram().getUuid(), programEncounter.getEncounterType().getUuid(), FormType.ProgramEncounter);
        if (formMapping == null) {
            throw new Exception(String.format("No form found for the encounter type %s", programEncounter.getEncounterType().getName()));
        }

        if (mode == EncounterUploadMode.SCHEDULE_VISIT) {
            programEncounter.setObservations(new ObservationCollection());
        } else {
            // Process observations for completed encounters without rule execution
            EncounterHeadersCreator encounterHeadersCreator = new EncounterHeadersCreator(strategyFactory);
            TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, encounterHeadersCreator, mode);
            programEncounter.setObservations(observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, FormType.ProgramEncounter, programEncounter.getObservations(), formMapping));
        }
        programEncounterService.save(programEncounter);
    }

    private ProgramEncounter getOrCreateProgramEncounter(Row row) {
        String id = row.get(EncounterHeadersCreator.ID);
        return createNewEncounter(id);
    }

    private ProgramEncounter createNewEncounter(String externalId) {
        ProgramEncounter programEncounter = new ProgramEncounter();
        if (StringUtils.hasText(externalId)) {
            programEncounter.setLegacyId(externalId);
        }
        programEncounter.setVoided(false);
        programEncounter.assignUUIDIfRequired();
        return programEncounter;
    }
}
