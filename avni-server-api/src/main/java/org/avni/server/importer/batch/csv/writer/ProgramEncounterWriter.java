package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.SubjectType;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeaderStrategyFactory;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ObservationService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ProgramEncounterService;
import org.avni.server.service.UserService;
import org.avni.server.util.DateTimeUtil;
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
public class ProgramEncounterWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final ProgramEncounterRepository programEncounterRepository;
    private ProgramEnrolmentCreator programEnrolmentCreator;
    private BasicEncounterCreator basicEncounterCreator;
    private FormMappingRepository formMappingRepository;
    private RuleServerInvoker ruleServerInvoker;
    private ObservationService observationService;
    private VisitCreator visitCreator;
    private DecisionCreator decisionCreator;
    private ProgramEnrolmentRepository programEnrolmentRepository;
    private ObservationCreator observationCreator;
    private ProgramEncounterService programEncounterService;
    private EntityApprovalStatusWriter entityApprovalStatusWriter;
    private EncounterHeaderStrategyFactory strategyFactory;
    private UserService userService;

    @Autowired
    public ProgramEncounterWriter(ProgramEncounterRepository programEncounterRepository,
                                  ProgramEnrolmentCreator programEnrolmentCreator,
                                  BasicEncounterCreator basicEncounterCreator,
                                  FormMappingRepository formMappingRepository,
                                  RuleServerInvoker ruleServerInvoker,
                                  ObservationService observationService,
                                  VisitCreator visitCreator,
                                  DecisionCreator decisionCreator,
                                  ProgramEnrolmentRepository programEnrolmentRepository,
                                  ObservationCreator observationCreator,
                                  ProgramEncounterService programEncounterService,
                                  EntityApprovalStatusWriter entityApprovalStatusWriter,
                                  OrganisationConfigService organisationConfigService, EncounterHeaderStrategyFactory strategyFactory, UserService userService) {
        super(organisationConfigService);
        this.programEncounterRepository = programEncounterRepository;
        this.programEnrolmentCreator = programEnrolmentCreator;
        this.basicEncounterCreator = basicEncounterCreator;
        this.formMappingRepository = formMappingRepository;
        this.ruleServerInvoker = ruleServerInvoker;
        this.observationService = observationService;
        this.visitCreator = visitCreator;
        this.decisionCreator = decisionCreator;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.observationCreator = observationCreator;
        this.programEncounterService = programEncounterService;
        this.entityApprovalStatusWriter = entityApprovalStatusWriter;
        this.strategyFactory = strategyFactory;
        this.userService = userService;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        String legacyId = row.get(EncounterHeadersCreator.ID);
        if (legacyId != null && !legacyId.trim().isEmpty()) {
            ProgramEncounter existingEncounter = programEncounterRepository.findByLegacyIdOrUuid(legacyId);
            if (existingEncounter != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", legacyId));
            }
        }
        ProgramEncounter programEncounter = getOrCreateProgramEncounter(row);
        ProgramEnrolment programEnrolment = programEnrolmentCreator.getProgramEnrolment(row.get(EncounterHeadersCreator.PROGRAM_ENROLMENT_ID), EncounterHeadersCreator.PROGRAM_ENROLMENT_ID);
        SubjectType subjectType = programEnrolment.getIndividual().getSubjectType();
        programEncounter.setProgramEnrolment(programEnrolment);
        basicEncounterCreator.updateEncounter(row, programEncounter, allErrorMsgs);

        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(subjectType.getUuid(), programEnrolment.getProgram().getUuid(), programEncounter.getEncounterType().getUuid(), FormType.ProgramEncounter);
        if (formMapping == null) {
            throw new Exception(String.format("No form found for the encounter type %s", programEncounter.getEncounterType().getName()));
        }

        boolean isScheduledVisit = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE) != null ||
                row.get(EncounterHeadersCreator.MAX_VISIT_DATE) != null;
        LocalDate encounterDate;

        if (isScheduledVisit) {
            String earliestDateStr = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE);
            String maxDateStr = row.get(EncounterHeadersCreator.MAX_VISIT_DATE);
            if (earliestDateStr == null || earliestDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
            }
            if (maxDateStr == null || maxDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.MAX_VISIT_DATE));
            }
            LocalDate earliestDate = DateTimeUtil.parseFlexibleDate(earliestDateStr);
            LocalDate maxDate = DateTimeUtil.parseFlexibleDate(maxDateStr);
            if (earliestDate != null && earliestDate.isAfter(LocalDate.now())) {
                allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
            }
            if (maxDate != null && maxDate.isAfter(LocalDate.now())) {
                allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.MAX_VISIT_DATE));
            }
            programEncounter.setEarliestVisitDateTime(earliestDate.toDateTimeAtStartOfDay());
            programEncounter.setMaxVisitDateTime(maxDate.toDateTimeAtStartOfDay());
        } else {
            String encounterDateStr = row.get(EncounterHeadersCreator.VISIT_DATE);
            if (encounterDateStr == null || encounterDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            }
            encounterDate = DateTimeUtil.parseFlexibleDate(encounterDateStr);
            if (encounterDate != null && encounterDate.isAfter(LocalDate.now())) {
                allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            }
            if (encounterDate != null) {
                LocalDate enrolmentDate = programEnrolment.getEnrolmentDateTime().toLocalDate();
                if (encounterDate.isBefore(enrolmentDate)) {
                    allErrorMsgs.add("Visit date needs to be after program enrolment date");
                }
            }
            programEncounter.setEncounterDateTime(encounterDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());
        }

        ValidationUtil.handleErrors(allErrorMsgs);
        ProgramEncounter savedEncounter;
        if (skipRuleExecution()) {
            EncounterHeadersCreator encounterHeadersCreator = new EncounterHeadersCreator(strategyFactory);
            TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, encounterHeadersCreator);
            programEncounter.setObservations(observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, FormType.ProgramEncounter, programEncounter.getObservations(), formMapping));
            savedEncounter = programEncounterService.save(programEncounter);
        } else {
            UploadRuleServerResponseContract ruleResponse = ruleServerInvoker.getRuleServerResult(row, formMapping.getForm(), programEncounter, allErrorMsgs);
            programEncounter.setObservations(observationService.createObservations(ruleResponse.getObservations()));
            decisionCreator.addEncounterDecisions(programEncounter.getObservations(), ruleResponse.getDecisions());
            decisionCreator.addEnrolmentDecisions(programEnrolment.getObservations(), ruleResponse.getDecisions());
            savedEncounter = programEncounterService.save(programEncounter);
            programEnrolmentRepository.save(programEnrolment);
            visitCreator.saveScheduledVisits(formMapping.getType(), null, programEnrolment.getUuid(), ruleResponse.getVisitSchedules(), savedEncounter.getUuid());
        }
        entityApprovalStatusWriter.saveStatus(formMapping, savedEncounter.getId(), EntityApprovalStatus.EntityType.ProgramEncounter, savedEncounter.getEncounterType().getUuid());
    }

    private ProgramEncounter getOrCreateProgramEncounter(Row row) {
        String id = row.get(EncounterHeadersCreator.ID);
        return createNewEncounter(id);
    }

    private ProgramEncounter createNewEncounter(String externalId) {
        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.setLegacyId(externalId);
        programEncounter.setVoided(false);
        programEncounter.assignUUIDIfRequired();
        return programEncounter;
    }
}
