package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ProgramEnrolmentService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEnrolmentRowCreator {
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final SubjectCreator subjectCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationCreator observationCreator;
    private final ProgramEnrolmentService programEnrolmentService;
    private final ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;
    private final ProgramRepository programRepository;
    private final ProgramEnrolmentCreator programEnrolmentCreator;

    @Autowired
    public ProgramEnrolmentRowCreator(ProgramEnrolmentRepository programEnrolmentRepository,
                                      SubjectCreator subjectCreator,
                                      FormMappingRepository formMappingRepository,
                                      ObservationCreator observationCreator,
                                      ProgramEnrolmentService programEnrolmentService,
                                      ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator,
                                      ProgramRepository programRepository,
                                      ProgramEnrolmentCreator programEnrolmentCreator) {
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.subjectCreator = subjectCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.programEnrolmentService = programEnrolmentService;
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
        this.programRepository = programRepository;
        this.programEnrolmentCreator = programEnrolmentCreator;
    }

    public void create(Row row, ProgramEnrolmentUploadMode mode) throws ValidationException, InvalidConfigurationException {
        ProgramEnrolmentUploadMode effectiveMode = mode == null ? ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT : mode;
        List<String> allErrorMsgs = new ArrayList<>();

        if (effectiveMode == ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT) {
            rejectMisplacedExitPrefixHeaders(row, allErrorMsgs);
            ValidationUtil.handleErrors(allErrorMsgs);
        }

        String id = row.get(ProgramEnrolmentHeadersCreator.id);
        if (id != null && !id.trim().isEmpty()) {
            ProgramEnrolment existingEnrolment = programEnrolmentRepository.findByLegacyIdOrUuid(id);
            if (existingEnrolment != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", id));
            }
        }
        String providedSubjectId = row.get(ProgramEnrolmentHeadersCreator.subjectId);
        Individual individual = subjectCreator.getSubject(providedSubjectId, ProgramEnrolmentHeadersCreator.subjectId, allErrorMsgs);
        String programNameProvided = row.get(ProgramEnrolmentHeadersCreator.programHeader);
        Program program = programRepository.findByName(programNameProvided);
        if (program == null) {
            ValidationUtil.fieldMissing("Program", programNameProvided, allErrorMsgs);
        }
        if (individual == null) {
            ValidationUtil.fieldMissing("Subject ID", providedSubjectId, allErrorMsgs);
        }
        ValidationUtil.handleErrors(allErrorMsgs);

        if (effectiveMode != ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT && !program.isAllowMultipleEnrolments() && programEnrolmentService.alreadyEnrolled(individual, program)) {
            allErrorMsgs.add(String.format("Subject '%s' is already enrolled in program '%s' and the program doesn't allow for multiple enrolments", providedSubjectId, program.getName()));
            ValidationUtil.handleErrors(allErrorMsgs);
        }

        FormMapping enrolmentFormMapping = formMappingRepository.getProgramEnrolmentFormMapping(individual.getSubjectType(), program);
        if (enrolmentFormMapping == null) {
            allErrorMsgs.add(String.format("No form found for the subject type '%s' and program '%s'", individual.getSubjectType().getName(), program.getName()));
        }
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), enrolmentFormMapping, programEnrolmentHeadersCreator, effectiveMode, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        ProgramEnrolment programEnrolment = getOrCreateProgramEnrolment(row);
        programEnrolment.setIndividual(individual);
        LocalDate enrolmentDate = row.ensureDateIsPresentAndNotInFuture(ProgramEnrolmentHeadersCreator.enrolmentDate, allErrorMsgs);
        if (enrolmentDate != null) programEnrolment.setEnrolmentDateTime(enrolmentDate.toDateTimeAtStartOfDay());

        LocationCreator locationCreator = new LocationCreator();
        programEnrolment.setEnrolmentLocation(locationCreator.getGeoLocation(row, ProgramEnrolmentHeadersCreator.enrolmentCoordinates, allErrorMsgs));
        programEnrolment.setProgram(program);

        Row enrolmentObservationsRow = effectiveMode == ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT
                ? rowExcludingHeadersWithPrefix(row, ProgramEnrolmentHeadersCreator.EXIT_CONCEPT_PREFIX)
                : row;
        programEnrolment.setObservations(observationCreator.getObservations(enrolmentObservationsRow, programEnrolmentHeadersCreator, allErrorMsgs, FormType.ProgramEnrolment, programEnrolment.getObservations(), enrolmentFormMapping));

        if (effectiveMode == ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT) {
            applyExitFields(row, programEnrolment, enrolmentDate, allErrorMsgs, locationCreator, individual, program, true);
        }

        ValidationUtil.handleErrors(allErrorMsgs);
        programEnrolmentService.save(programEnrolment);
    }

    /**
     * Exits an enrolment that is already in Avni. The row names the enrolment, so the program and subject type
     * come from the upload type picked on the upload screen, carried here as its enrolment form mapping.
     */
    public void exit(Row row, FormMapping enrolmentFormMapping) throws ValidationException, InvalidConfigurationException {
        List<String> allErrorMsgs = new ArrayList<>();
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), enrolmentFormMapping, programEnrolmentHeadersCreator, ProgramEnrolmentUploadMode.EXIT_ENROLMENT, allErrorMsgs);

        String enrolmentId = row.get(EncounterHeadersCreator.PROGRAM_ENROLMENT_ID);
        ProgramEnrolment programEnrolment = programEnrolmentCreator.getProgramEnrolment(enrolmentId, EncounterHeadersCreator.PROGRAM_ENROLMENT_ID, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);
        checkEnrolmentCanBeExited(programEnrolment, enrolmentId, enrolmentFormMapping, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        LocalDate enrolmentDate = programEnrolment.getEnrolmentDateTime().toLocalDate();
        applyExitFields(row, programEnrolment, enrolmentDate, allErrorMsgs, new LocationCreator(), programEnrolment.getIndividual(), programEnrolment.getProgram(), false);

        ValidationUtil.handleErrors(allErrorMsgs);
        programEnrolmentService.save(programEnrolment);
    }

    // The enrolment lookup does not filter voided rows, and voiding a subject leaves its enrolments live.
    // Rejecting an already exited enrolment is what makes uploading the same file twice safe.
    private static void checkEnrolmentCanBeExited(ProgramEnrolment programEnrolment, String enrolmentId, FormMapping enrolmentFormMapping, List<String> allErrorMsgs) {
        if (programEnrolment.isVoided()) {
            allErrorMsgs.add(String.format("Program enrolment '%s' is voided", enrolmentId));
        }
        if (programEnrolment.getIndividual().isVoided()) {
            allErrorMsgs.add(String.format("The subject of program enrolment '%s' is voided", enrolmentId));
        }
        String programName = programEnrolment.getProgram().getName();
        String uploadProgramName = enrolmentFormMapping.getProgram().getName();
        if (!programName.equals(uploadProgramName)) {
            allErrorMsgs.add(String.format("Program enrolment '%s' is in program '%s', not '%s'", enrolmentId, programName, uploadProgramName));
        }
        String subjectTypeName = programEnrolment.getIndividual().getSubjectType().getName();
        String uploadSubjectTypeName = enrolmentFormMapping.getSubjectType().getName();
        if (!subjectTypeName.equals(uploadSubjectTypeName)) {
            allErrorMsgs.add(String.format("Program enrolment '%s' is for subject type '%s', not '%s'", enrolmentId, subjectTypeName, uploadSubjectTypeName));
        }
        if (programEnrolment.isExited()) {
            allErrorMsgs.add(String.format("Program enrolment '%s' is already exited (exit date %s)", enrolmentId, programEnrolment.getProgramExitDateTime().toLocalDate()));
        }
    }

    private void applyExitFields(Row row,
                                 ProgramEnrolment programEnrolment,
                                 LocalDate enrolmentDate,
                                 List<String> allErrorMsgs,
                                 LocationCreator locationCreator,
                                 Individual individual,
                                 Program program,
                                 boolean prefixedExitColumns) throws ValidationException {
        String exitDateStr = row.get(ProgramEnrolmentHeadersCreator.exitDate);
        if (!StringUtils.hasText(exitDateStr)) {
            allErrorMsgs.add(String.format("'%s' is mandatory for exited enrolments", ProgramEnrolmentHeadersCreator.exitDate));
        } else {
            LocalDate exitDate = parseExitDate(exitDateStr);
            if (exitDate == null) {
                allErrorMsgs.add(String.format("Invalid date format for '%s'. Expected format: DD-MM-YYYY or YYYY-MM-DD", ProgramEnrolmentHeadersCreator.exitDate));
            } else {
                if (exitDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", ProgramEnrolmentHeadersCreator.exitDate));
                }
                if (enrolmentDate != null && exitDate.isBefore(enrolmentDate)) {
                    // A historical exited enrolment row carries its own Enrolment Date column; an exit of an existing
                    // enrolment does not, so it names the enrolment's own date instead.
                    allErrorMsgs.add(prefixedExitColumns
                            ? String.format("'%s' needs to be on or after '%s'", ProgramEnrolmentHeadersCreator.exitDate, ProgramEnrolmentHeadersCreator.enrolmentDate)
                            : String.format("'%s' needs to be on or after the enrolment date (%s)", ProgramEnrolmentHeadersCreator.exitDate, enrolmentDate));
                }
                programEnrolment.setProgramExitDateTime(exitDate.toDateTimeAtStartOfDay());
            }
        }
        programEnrolment.setExitLocation(locationCreator.getGeoLocation(row, ProgramEnrolmentHeadersCreator.exitCoordinates, allErrorMsgs));

        FormMapping programExitFormMapping = formMappingRepository.getProgramExitFormMapping(individual.getSubjectType(), program);
        ObservationCollection exitObservations;
        if (programExitFormMapping == null) {
            exitObservations = new ObservationCollection();
        } else {
            // Historical exited enrolments carry both forms in one row, so their exit columns are prefixed. Exiting an
            // existing enrolment carries only the exit form, unprefixed, and answers are matched by the form's questions.
            Row exitObservationsRow = prefixedExitColumns ? rowOfExitConcepts(row, ProgramEnrolmentHeadersCreator.EXIT_CONCEPT_PREFIX) : row;
            exitObservations = observationCreator.getObservations(exitObservationsRow, programEnrolmentHeadersCreator, allErrorMsgs, FormType.ProgramExit, programEnrolment.getProgramExitObservations(), programExitFormMapping);
        }
        programEnrolment.setProgramExitObservations(exitObservations);
    }

    private static LocalDate parseExitDate(String exitDateStr) {
        try {
            return DateTimeUtil.parseFlexibleDate(exitDateStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void rejectMisplacedExitPrefixHeaders(Row row, List<String> allErrorMsgs) {
        for (String header : row.getHeaders()) {
            if (header == null) continue;
            String unwrapped = stripSurroundingQuotes(header);
            int prefixIndex = unwrapped.indexOf(ProgramEnrolmentHeadersCreator.EXIT_CONCEPT_PREFIX);
            if (prefixIndex > 0) {
                allErrorMsgs.add(String.format("Header '%s' contains '%s' but not at the start of the column name. The prefix is only allowed at the beginning of an exit-form concept column.", header, ProgramEnrolmentHeadersCreator.EXIT_CONCEPT_PREFIX));
            }
        }
    }

    private static String stripSurroundingQuotes(String header) {
        String trimmed = header.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Row rowExcludingHeadersWithPrefix(Row row, String prefix) {
        String[] originalHeaders = row.getHeaders();
        List<String> filteredHeaders = new ArrayList<>();
        List<String> filteredValues = new ArrayList<>();
        for (String header : originalHeaders) {
            if (header == null) continue;
            String unwrapped = stripSurroundingQuotes(header);
            if (unwrapped.startsWith(prefix)) continue;
            filteredHeaders.add(header);
            filteredValues.add(row.get(header));
        }
        return new Row(filteredHeaders.toArray(new String[0]), filteredValues.toArray(new String[0]));
    }

    private static Row rowOfExitConcepts(Row row, String prefix) {
        String[] originalHeaders = row.getHeaders();
        List<String> filteredHeaders = new ArrayList<>();
        List<String> filteredValues = new ArrayList<>();
        for (String header : originalHeaders) {
            if (header == null) continue;
            String unwrapped = stripSurroundingQuotes(header);
            if (!unwrapped.startsWith(prefix)) continue;
            String strippedHeader = unwrapped.substring(prefix.length());
            String rewrapped = header.trim().startsWith("\"") ? "\"" + strippedHeader + "\"" : strippedHeader;
            filteredHeaders.add(rewrapped);
            filteredValues.add(row.get(header));
        }
        return new Row(filteredHeaders.toArray(new String[0]), filteredValues.toArray(new String[0]));
    }

    private ProgramEnrolment getOrCreateProgramEnrolment(Row row) {
        String id = row.get(ProgramEnrolmentHeadersCreator.id);
        return createNewEnrolment(id);
    }

    private ProgramEnrolment createNewEnrolment(String externalId) {
        ProgramEnrolment programEnrolment = new ProgramEnrolment();
        if (StringUtils.hasText(externalId)) {
            programEnrolment.setLegacyId(externalId);
        }
        programEnrolment.setVoided(false);
        programEnrolment.assignUUIDIfRequired();
        return programEnrolment;
    }
}
