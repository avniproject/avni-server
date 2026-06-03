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

    @Autowired
    public ProgramEnrolmentRowCreator(ProgramEnrolmentRepository programEnrolmentRepository,
                                      SubjectCreator subjectCreator,
                                      FormMappingRepository formMappingRepository,
                                      ObservationCreator observationCreator,
                                      ProgramEnrolmentService programEnrolmentService,
                                      ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator,
                                      ProgramRepository programRepository) {
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.subjectCreator = subjectCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.programEnrolmentService = programEnrolmentService;
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
        this.programRepository = programRepository;
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

        if (!program.isAllowMultipleEnrolments() && programEnrolmentService.alreadyEnrolled(individual, program)) {
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
            applyExitFields(row, programEnrolment, enrolmentDate, allErrorMsgs, locationCreator, individual, program);
        }

        ValidationUtil.handleErrors(allErrorMsgs);
        programEnrolmentService.save(programEnrolment);
    }

    private void applyExitFields(Row row,
                                 ProgramEnrolment programEnrolment,
                                 LocalDate enrolmentDate,
                                 List<String> allErrorMsgs,
                                 LocationCreator locationCreator,
                                 Individual individual,
                                 Program program) throws ValidationException {
        String exitDateStr = row.get(ProgramEnrolmentHeadersCreator.exitDate);
        if (!StringUtils.hasText(exitDateStr)) {
            allErrorMsgs.add(String.format("'%s' is mandatory for exited enrolments", ProgramEnrolmentHeadersCreator.exitDate));
        } else {
            LocalDate exitDate = DateTimeUtil.parseFlexibleDate(exitDateStr);
            if (exitDate == null) {
                allErrorMsgs.add(String.format("Invalid date format for '%s'. Expected format: DD-MM-YYYY or YYYY-MM-DD", ProgramEnrolmentHeadersCreator.exitDate));
            } else {
                if (exitDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", ProgramEnrolmentHeadersCreator.exitDate));
                }
                if (enrolmentDate != null && exitDate.isBefore(enrolmentDate)) {
                    allErrorMsgs.add(String.format("'%s' needs to be on or after '%s'", ProgramEnrolmentHeadersCreator.exitDate, ProgramEnrolmentHeadersCreator.enrolmentDate));
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
            Row exitObservationsRow = rowOfExitConcepts(row, ProgramEnrolmentHeadersCreator.EXIT_CONCEPT_PREFIX);
            exitObservations = observationCreator.getObservations(exitObservationsRow, programEnrolmentHeadersCreator, allErrorMsgs, FormType.ProgramExit, programEnrolment.getProgramExitObservations(), programExitFormMapping);
        }
        programEnrolment.setProgramExitObservations(exitObservations);
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
