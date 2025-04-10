package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ProgramEnrolmentService;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Component
public class ProgramEnrolmentWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final SubjectCreator subjectCreator;
    private final DateCreator dateCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationCreator observationCreator;
    private final ProgramEnrolmentService programEnrolmentService;
    private final ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;
    private final ProgramRepository programRepository;

    @Autowired
    public ProgramEnrolmentWriter(ProgramEnrolmentRepository programEnrolmentRepository,
                                  SubjectCreator subjectCreator,
                                  FormMappingRepository formMappingRepository,
                                  ObservationCreator observationCreator,
                                  ProgramEnrolmentService programEnrolmentService,
                                  OrganisationConfigService organisationConfigService,
                                  ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator, ProgramRepository programRepository) {
        super(organisationConfigService);
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.subjectCreator = subjectCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.programEnrolmentService = programEnrolmentService;
        this.dateCreator = new DateCreator();
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
        this.programRepository = programRepository;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws ValidationException {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws ValidationException {
        List<String> allErrorMsgs = new ArrayList<>();
        String providedSubjectId = row.get(ProgramEnrolmentHeadersCreator.subjectId);
        Individual individual = subjectCreator.getSubject(providedSubjectId, allErrorMsgs, ProgramEnrolmentHeadersCreator.subjectId);
        String programNameProvided = row.get(ProgramEnrolmentHeadersCreator.programHeader);
        Program program = programRepository.findByName(programNameProvided);
        if (program == null) {
            ValidationUtil.fieldMissing("Program", programNameProvided, allErrorMsgs);
        }
        ValidationUtil.handleErrors(allErrorMsgs);

        FormMapping formMapping = formMappingRepository.getProgramEnrolmentFormMapping(individual.getSubjectType(), program);
        if (formMapping == null) {
            allErrorMsgs.add(String.format("No form found for the subject type '%s' and program '%s'", individual.getSubjectType().getName(), program.getName()));
        }
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, programEnrolmentHeadersCreator);

        ProgramEnrolment programEnrolment = getOrCreateProgramEnrolment(row);
        programEnrolment.setIndividual(individual);
        LocalDate enrolmentDate = dateCreator.getDate(
                row,
                ProgramEnrolmentHeadersCreator.enrolmentDate,
                allErrorMsgs, String.format("%s is mandatory", ProgramEnrolmentHeadersCreator.enrolmentDate)
        );
        if (enrolmentDate != null) programEnrolment.setEnrolmentDateTime(enrolmentDate.toDateTimeAtStartOfDay());

        LocationCreator locationCreator = new LocationCreator();
        programEnrolment.setEnrolmentLocation(locationCreator.getGeoLocation(row, ProgramEnrolmentHeadersCreator.enrolmentLocation, allErrorMsgs));
        programEnrolment.setProgram(program);

        programEnrolment.setObservations(observationCreator.getObservations(row, programEnrolmentHeadersCreator, allErrorMsgs, FormType.ProgramEnrolment, programEnrolment.getObservations(), formMapping));
        programEnrolmentService.save(programEnrolment);
    }

    private ProgramEnrolment getOrCreateProgramEnrolment(Row row) {
        String id = row.get(ProgramEnrolmentHeadersCreator.id);
        ProgramEnrolment existingEnrolment = null;
        if (id != null && !id.isEmpty()) {
            existingEnrolment = programEnrolmentRepository.findByLegacyIdOrUuid(id);
        }
        return existingEnrolment == null ? createNewEnrolment(id) : existingEnrolment;
    }

    private ProgramEnrolment createNewEnrolment(String externalId) {
        ProgramEnrolment programEnrolment = new ProgramEnrolment();
        programEnrolment.setLegacyId(externalId);
        programEnrolment.setVoided(false);
        programEnrolment.assignUUIDIfRequired();
        return programEnrolment;
    }
}
