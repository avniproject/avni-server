package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ObservationService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ProgramEnrolmentService;
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
    private final ProgramCreator programCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationService observationService;
    private final RuleServerInvoker ruleServerInvoker;
    private final VisitCreator visitCreator;
    private final DecisionCreator decisionCreator;
    private final ObservationCreator observationCreator;
    private final ProgramEnrolmentService programEnrolmentService;
    private final EntityApprovalStatusWriter entityApprovalStatusWriter;
    private final ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;

    @Autowired
    public ProgramEnrolmentWriter(ProgramEnrolmentRepository programEnrolmentRepository,
                                  SubjectCreator subjectCreator,
                                  ProgramCreator programCreator,
                                  FormMappingRepository formMappingRepository,
                                  ObservationService observationService,
                                  RuleServerInvoker ruleServerInvoker,
                                  VisitCreator visitCreator,
                                  DecisionCreator decisionCreator,
                                  ObservationCreator observationCreator,
                                  ProgramEnrolmentService programEnrolmentService,
                                  EntityApprovalStatusWriter entityApprovalStatusWriter,
                                  OrganisationConfigService organisationConfigService,
                                  ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator) {
        super(organisationConfigService);
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.subjectCreator = subjectCreator;
        this.programCreator = programCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationService = observationService;
        this.ruleServerInvoker = ruleServerInvoker;
        this.visitCreator = visitCreator;
        this.decisionCreator = decisionCreator;
        this.observationCreator = observationCreator;
        this.programEnrolmentService = programEnrolmentService;
        this.entityApprovalStatusWriter = entityApprovalStatusWriter;
        this.dateCreator = new DateCreator();
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws Exception {
        ProgramEnrolment programEnrolment = getOrCreateProgramEnrolment(row);

        List<String> allErrorMsgs = new ArrayList<>();
        Individual individual = subjectCreator.getSubject(row.get(ProgramEnrolmentHeadersCreator.subjectId), allErrorMsgs, ProgramEnrolmentHeadersCreator.subjectId);
        programEnrolment.setIndividual(individual);
        Program program = programCreator.getProgram(row.get(ProgramEnrolmentHeadersCreator.programHeader), ProgramEnrolmentHeadersCreator.programHeader);
        LocalDate enrolmentDate = dateCreator.getDate(
                row,
                ProgramEnrolmentHeadersCreator.enrolmentDate,
                allErrorMsgs, String.format("%s is mandatory", ProgramEnrolmentHeadersCreator.enrolmentDate)
        );
        if (enrolmentDate != null) programEnrolment.setEnrolmentDateTime(enrolmentDate.toDateTimeAtStartOfDay());
        LocalDate exitDate = dateCreator.getDate(
                row,
                ProgramEnrolmentHeadersCreator.exitDate,
                allErrorMsgs, null
        );
        if (exitDate != null) programEnrolment.setProgramExitDateTime(exitDate.toDateTimeAtStartOfDay());

        LocationCreator locationCreator = new LocationCreator();
        programEnrolment.setEnrolmentLocation(locationCreator.getGeoLocation(row, ProgramEnrolmentHeadersCreator.enrolmentLocation, allErrorMsgs));
        programEnrolment.setExitLocation(locationCreator.getGeoLocation(row, ProgramEnrolmentHeadersCreator.exitLocation, allErrorMsgs));
        programEnrolment.setProgram(program);
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(individual.getSubjectType().getUuid(), program.getUuid(), null, FormType.ProgramEnrolment);
        if (formMapping == null) {
            throw new Exception(String.format("No form found for the subject type '%s' and program '%s'", individual.getSubjectType().getName(), program.getName()));
        }
        ProgramEnrolment savedEnrolment;
        if (skipRuleExecution()) {
            programEnrolment.setObservations(observationCreator.getObservations(row, programEnrolmentHeadersCreator, allErrorMsgs, FormType.ProgramEnrolment, programEnrolment.getObservations(), formMapping));
            savedEnrolment = programEnrolmentService.save(programEnrolment);
        } else {
            UploadRuleServerResponseContract ruleResponse = ruleServerInvoker.getRuleServerResult(row, formMapping.getForm(), programEnrolment, allErrorMsgs);
            programEnrolment.setObservations(observationService.createObservations(ruleResponse.getObservations()));
            decisionCreator.addEnrolmentDecisions(programEnrolment.getObservations(), ruleResponse.getDecisions());
            savedEnrolment = programEnrolmentService.save(programEnrolment);
            visitCreator.saveScheduledVisits(formMapping.getType(), null, savedEnrolment.getUuid(), ruleResponse.getVisitSchedules(), null);
        }
        entityApprovalStatusWriter.saveStatus(formMapping, savedEnrolment.getId(), EntityApprovalStatus.EntityType.ProgramEnrolment, savedEnrolment.getProgram().getUuid());
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
