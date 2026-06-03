package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.EncounterService;
import org.avni.server.service.ProgramEncounterService;
import org.avni.server.service.UserService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
public class EncounterCreator {
    private final EncounterRepository encounterRepository;
    private final ProgramEncounterRepository programEncounterRepository;
    private final BasicEncounterCreator basicEncounterCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationCreator observationCreator;
    private final EncounterService encounterService;
    private final ProgramEncounterService programEncounterService;
    private final UserService userService;
    private final SubjectCreator subjectCreator;
    private final ProgramEnrolmentCreator programEnrolmentCreator;

    @Autowired
    public EncounterCreator(EncounterRepository encounterRepository,
                            ProgramEncounterRepository programEncounterRepository,
                            BasicEncounterCreator basicEncounterCreator,
                            FormMappingRepository formMappingRepository,
                            ObservationCreator observationCreator,
                            EncounterService encounterService,
                            ProgramEncounterService programEncounterService,
                            UserService userService,
                            SubjectCreator subjectCreator,
                            ProgramEnrolmentCreator programEnrolmentCreator) {
        this.encounterRepository = encounterRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.basicEncounterCreator = basicEncounterCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.encounterService = encounterService;
        this.programEncounterService = programEncounterService;
        this.userService = userService;
        this.subjectCreator = subjectCreator;
        this.programEnrolmentCreator = programEnrolmentCreator;
    }

    public void createForSubject(Row row, String encounterUploadMode) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        rejectIfLegacyIdAlreadyPresent(row, allErrorMsgs, encounterRepository::findByLegacyIdOrUuid);
        EncounterUploadMode mode = parseMode(encounterUploadMode);

        Encounter encounter = createNewGeneralEncounter(row);
        basicEncounterCreator.updateEncounterFields(row, encounter, allErrorMsgs, mode);
        ValidationUtil.handleErrors(allErrorMsgs);

        String subjectId = row.get(EncounterHeadersCreator.SUBJECT_ID);
        Individual subject = subjectCreator.getSubject(subjectId, EncounterHeadersCreator.SUBJECT_ID, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);
        encounter.setIndividual(subject);

        processEncounter(row, encounter, subject.getSubjectType(), null, null, mode, allErrorMsgs,
                FormType.Encounter, FormType.IndividualEncounterCancellation);
        encounterService.save(encounter);
    }

    public void createForEnrolment(Row row, String encounterUploadMode) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        rejectIfLegacyIdAlreadyPresent(row, allErrorMsgs, programEncounterRepository::findByLegacyIdOrUuid);
        EncounterUploadMode mode = parseMode(encounterUploadMode);

        ProgramEncounter programEncounter = createNewProgramEncounter(row);
        basicEncounterCreator.updateEncounterFields(row, programEncounter, allErrorMsgs, mode);
        ValidationUtil.handleErrors(allErrorMsgs);

        String enrolmentId = row.get(EncounterHeadersCreator.PROGRAM_ENROLMENT_ID);
        ProgramEnrolment programEnrolment = programEnrolmentCreator.getProgramEnrolment(enrolmentId, EncounterHeadersCreator.PROGRAM_ENROLMENT_ID, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);
        programEncounter.setProgramEnrolment(programEnrolment);

        processEncounter(row, programEncounter,
                programEnrolment.getIndividual().getSubjectType(),
                programEnrolment.getProgram(),
                programEnrolment,
                mode, allErrorMsgs,
                FormType.ProgramEncounter, FormType.ProgramEncounterCancellation);
        programEncounterService.save(programEncounter);
    }

    private <T> void rejectIfLegacyIdAlreadyPresent(Row row, List<String> allErrorMsgs, Function<String, T> lookupByLegacyIdOrUuid) throws ValidationException {
        String legacyId = row.get(EncounterHeadersCreator.ID);
        if (legacyId == null || legacyId.trim().isEmpty()) return;
        T existing = lookupByLegacyIdOrUuid.apply(legacyId);
        if (existing != null) {
            allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", legacyId));
            ValidationUtil.handleErrors(allErrorMsgs);
        }
    }

    private void processEncounter(Row row,
                                  AbstractEncounter encounter,
                                  SubjectType subjectType,
                                  @Nullable Program program,
                                  @Nullable ProgramEnrolment programEnrolment,
                                  EncounterUploadMode mode,
                                  List<String> allErrorMsgs,
                                  FormType observationFormType,
                                  FormType cancellationFormType) throws Exception {
        FormMapping observationFormMapping = formMappingRepository.getRequiredFormMapping(
                subjectType.getUuid(),
                program == null ? null : program.getUuid(),
                encounter.getEncounterType().getUuid(),
                observationFormType);
        if (observationFormMapping == null) {
            throw new Exception(String.format("No form found for the encounter type %s", encounter.getEncounterType().getName()));
        }
        FormMapping cancellationFormMapping = mode == EncounterUploadMode.UPLOAD_CANCELLED_VISIT
                ? resolveCancellationFormMapping(programEnrolment, subjectType, program, encounter)
                : null;

        EncounterHeadersCreator encounterHeadersCreator = new EncounterHeadersCreator(formMappingRepository);
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), observationFormMapping, encounterHeadersCreator, mode, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        applyModeSpecificFields(row, encounter, programEnrolment, mode, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        applyObservations(row, encounter, mode, observationFormType, cancellationFormType, observationFormMapping, cancellationFormMapping, encounterHeadersCreator, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);
    }

    private FormMapping resolveCancellationFormMapping(@Nullable ProgramEnrolment programEnrolment,
                                                       SubjectType subjectType,
                                                       @Nullable Program program,
                                                       AbstractEncounter encounter) {
        if (programEnrolment == null) {
            return formMappingRepository.getGeneralEncounterCancelFormMapping(subjectType, encounter.getEncounterType());
        }
        return formMappingRepository.getProgramEncounterCancelFormMapping(subjectType, program, encounter.getEncounterType());
    }

    private void applyModeSpecificFields(Row row, AbstractEncounter encounter, @Nullable ProgramEnrolment programEnrolment, EncounterUploadMode mode, List<String> allErrorMsgs) {
        switch (mode) {
            case SCHEDULE_VISIT:
                applyScheduleVisitFields(row, encounter, programEnrolment, allErrorMsgs);
                break;
            case UPLOAD_VISIT_DETAILS:
                applyUploadVisitFields(row, encounter, programEnrolment, allErrorMsgs);
                break;
            case UPLOAD_CANCELLED_VISIT:
                applyCancelledVisitFields(row, encounter, programEnrolment, allErrorMsgs);
                break;
        }
    }

    private void applyObservations(Row row,
                                   AbstractEncounter encounter,
                                   EncounterUploadMode mode,
                                   FormType observationFormType,
                                   FormType cancellationFormType,
                                   FormMapping observationFormMapping,
                                   @Nullable FormMapping cancellationFormMapping,
                                   EncounterHeadersCreator encounterHeadersCreator,
                                   List<String> allErrorMsgs) throws ValidationException {
        switch (mode) {
            case SCHEDULE_VISIT:
                encounter.setObservations(new ObservationCollection());
                break;
            case UPLOAD_VISIT_DETAILS:
                encounter.setObservations(observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, observationFormType, encounter.getObservations(), observationFormMapping));
                break;
            case UPLOAD_CANCELLED_VISIT:
                encounter.setObservations(new ObservationCollection());
                ObservationCollection cancelObservations = cancellationFormMapping == null
                        ? new ObservationCollection()
                        : observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, cancellationFormType, encounter.getCancelObservations(), cancellationFormMapping);
                encounter.setCancelObservations(cancelObservations);
                break;
        }
    }

    private EncounterUploadMode parseMode(String encounterUploadMode) {
        return Optional.ofNullable(EncounterUploadMode.fromString(encounterUploadMode))
                .orElse(EncounterUploadMode.UPLOAD_VISIT_DETAILS);
    }

    private void applyScheduleVisitFields(Row row, AbstractEncounter encounter, @Nullable ProgramEnrolment programEnrolment, List<String> allErrorMsgs) {
        String earliestDateStr = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE);
        String maxDateStr = row.get(EncounterHeadersCreator.MAX_VISIT_DATE);
        LocalDate enrolmentDate = programEnrolment == null ? null : programEnrolment.getEnrolmentDateTime().toLocalDate();

        LocalDate earliestDate = null;
        if (!StringUtils.hasText(earliestDateStr)) {
            allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
        } else {
            earliestDate = DateTimeUtil.parseFlexibleDate(earliestDateStr);
            if (earliestDate != null && enrolmentDate != null && earliestDate.isBefore(enrolmentDate)) {
                allErrorMsgs.add("Earliest visit date needs to be after program enrolment date");
            }
            encounter.setEarliestVisitDateTime(earliestDate != null ? earliestDate.toDateTimeAtStartOfDay() : null);
        }
        if (!StringUtils.hasText(maxDateStr)) {
            allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.MAX_VISIT_DATE));
        } else {
            LocalDate maxDate = DateTimeUtil.parseFlexibleDate(maxDateStr);
            if (maxDate != null && earliestDate != null && maxDate.isBefore(earliestDate)) {
                allErrorMsgs.add("Max visit date needs to be after Earliest visit date");
            }
            encounter.setMaxVisitDateTime(maxDate != null ? maxDate.toDateTimeAtStartOfDay() : null);
        }
        encounter.setName(encounter.getEncounterType().getName());
    }

    private void applyUploadVisitFields(Row row, AbstractEncounter encounter, @Nullable ProgramEnrolment programEnrolment, List<String> allErrorMsgs) {
        String encounterDateStr = row.get(EncounterHeadersCreator.VISIT_DATE);
        if (encounterDateStr == null || encounterDateStr.trim().isEmpty()) {
            allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            return;
        }
        LocalDate encounterDate = DateTimeUtil.parseFlexibleDate(encounterDateStr);
        if (encounterDate == null) {
            allErrorMsgs.add(String.format("Invalid date format for '%s'. Expected format: DD-MM-YYYY or YYYY-MM-DD", EncounterHeadersCreator.VISIT_DATE));
            return;
        }
        if (encounterDate.isAfter(LocalDate.now())) {
            allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.VISIT_DATE));
        }
        if (programEnrolment != null) {
            LocalDate enrolmentDate = programEnrolment.getEnrolmentDateTime().toLocalDate();
            if (encounterDate.isBefore(enrolmentDate)) {
                allErrorMsgs.add("Visit date needs to be after program enrolment date");
            }
        }
        encounter.setEncounterDateTime(encounterDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());
        validateOptionalScheduleWindowOrdering(row, allErrorMsgs);
    }

    private void validateOptionalScheduleWindowOrdering(Row row, List<String> allErrorMsgs) {
        String earliestDateStr = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE);
        String maxDateStr = row.get(EncounterHeadersCreator.MAX_VISIT_DATE);
        if (!StringUtils.hasText(earliestDateStr) || !StringUtils.hasText(maxDateStr)) {
            return;
        }
        LocalDate earliestDate = DateTimeUtil.parseFlexibleDate(earliestDateStr);
        LocalDate maxDate = DateTimeUtil.parseFlexibleDate(maxDateStr);
        if (earliestDate != null && maxDate != null && maxDate.isBefore(earliestDate)) {
            allErrorMsgs.add("Max visit date needs to be after Earliest visit date");
        }
    }

    private void applyCancelledVisitFields(Row row, AbstractEncounter encounter, @Nullable ProgramEnrolment programEnrolment, List<String> allErrorMsgs) {
        DateCreator dateCreator = new DateCreator();
        LocalDate enrolmentDate = programEnrolment == null ? null : programEnrolment.getEnrolmentDateTime().toLocalDate();

        LocalDate earliestDate = dateCreator.getDate(row, EncounterHeadersCreator.EARLIEST_VISIT_DATE, allErrorMsgs,
                String.format("'%s' is mandatory for cancelled visits", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
        LocalDate maxDate = dateCreator.getDate(row, EncounterHeadersCreator.MAX_VISIT_DATE, allErrorMsgs,
                String.format("'%s' is mandatory for cancelled visits", EncounterHeadersCreator.MAX_VISIT_DATE));
        LocalDate cancelDate = dateCreator.getDate(row, EncounterHeadersCreator.CANCEL_DATE, allErrorMsgs,
                String.format("'%s' is mandatory for cancelled visits", EncounterHeadersCreator.CANCEL_DATE));

        if (earliestDate != null && enrolmentDate != null && earliestDate.isBefore(enrolmentDate)) {
            allErrorMsgs.add("Earliest visit date needs to be after program enrolment date");
        }
        if (maxDate != null && earliestDate != null && maxDate.isBefore(earliestDate)) {
            allErrorMsgs.add("Max visit date needs to be after Earliest visit date");
        }
        if (cancelDate != null && cancelDate.isAfter(LocalDate.now())) {
            allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.CANCEL_DATE));
        }
        if (cancelDate != null && earliestDate != null && cancelDate.isBefore(earliestDate)) {
            allErrorMsgs.add(String.format("'%s' needs to be on or after '%s'", EncounterHeadersCreator.CANCEL_DATE, EncounterHeadersCreator.EARLIEST_VISIT_DATE));
        }
        if (cancelDate != null && enrolmentDate != null && cancelDate.isBefore(enrolmentDate)) {
            allErrorMsgs.add(String.format("'%s' needs to be on or after program enrolment date", EncounterHeadersCreator.CANCEL_DATE));
        }

        if (earliestDate != null) encounter.setEarliestVisitDateTime(earliestDate.toDateTimeAtStartOfDay());
        if (maxDate != null) encounter.setMaxVisitDateTime(maxDate.toDateTimeAtStartOfDay());
        if (cancelDate != null) encounter.setCancelDateTime(cancelDate.toDateTimeAtStartOfDay());

        LocationCreator locationCreator = new LocationCreator();
        encounter.setCancelLocation(locationCreator.getGeoLocation(row, EncounterHeadersCreator.CANCEL_LOCATION, allErrorMsgs));
        encounter.setName(encounter.getEncounterType().getName());
    }

    private Encounter createNewGeneralEncounter(Row row) {
        String externalId = row.get(EncounterHeadersCreator.ID);
        Encounter encounter = new Encounter();
        if (StringUtils.hasText(externalId)) {
            encounter.setLegacyId(externalId);
        }
        encounter.setVoided(false);
        encounter.assignUUIDIfRequired();
        return encounter;
    }

    private ProgramEncounter createNewProgramEncounter(Row row) {
        String externalId = row.get(EncounterHeadersCreator.ID);
        ProgramEncounter programEncounter = new ProgramEncounter();
        if (StringUtils.hasText(externalId)) {
            programEncounter.setLegacyId(externalId);
        }
        programEncounter.setVoided(false);
        programEncounter.assignUUIDIfRequired();
        return programEncounter;
    }
}
