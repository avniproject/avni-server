package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeaderStrategyFactory;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.EncounterService;
import org.avni.server.service.ObservationService;
import org.avni.server.service.OrganisationConfigService;
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
public class EncounterWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final EncounterRepository encounterRepository;
    private final BasicEncounterCreator basicEncounterCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationService observationService;
    private final RuleServerInvoker ruleServerInvoker;
    private final VisitCreator visitCreator;
    private final DecisionCreator decisionCreator;
    private final ObservationCreator observationCreator;
    private final EncounterService encounterService;
    private final EntityApprovalStatusWriter entityApprovalStatusWriter;
    private final EncounterHeaderStrategyFactory strategyFactory;
    private final UserService userService;

    @Autowired
    public EncounterWriter(EncounterRepository encounterRepository,
                           BasicEncounterCreator basicEncounterCreator,
                           FormMappingRepository formMappingRepository,
                           ObservationService observationService,
                           RuleServerInvoker ruleServerInvoker,
                           VisitCreator visitCreator,
                           DecisionCreator decisionCreator,
                           ObservationCreator observationCreator,
                           EncounterService encounterService,
                           EntityApprovalStatusWriter entityApprovalStatusWriter,
                           OrganisationConfigService organisationConfigService, EncounterHeaderStrategyFactory strategyFactory, UserService userService) {
        super(organisationConfigService);
        this.encounterRepository = encounterRepository;
        this.basicEncounterCreator = basicEncounterCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationService = observationService;
        this.ruleServerInvoker = ruleServerInvoker;
        this.visitCreator = visitCreator;
        this.decisionCreator = decisionCreator;
        this.observationCreator = observationCreator;
        this.encounterService = encounterService;
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
            Encounter existingEncounter = encounterRepository.findByLegacyIdOrUuid(legacyId);
            if (existingEncounter != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", legacyId));
            }
        }
        Encounter encounter = getOrCreateEncounter(row);
        basicEncounterCreator.updateEncounter(row, encounter, allErrorMsgs);
        encounter.setVoided(false);
        encounter.assignUUIDIfRequired();
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(null, null, encounter.getEncounterType().getUuid(), FormType.Encounter);
        if (formMapping == null) {
            throw new Exception(String.format("No form found for the encounter type %s", encounter.getEncounterType().getName()));
        }

        boolean isScheduledVisit = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE) != null;
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
            encounter.setEarliestVisitDateTime(earliestDate != null ? earliestDate.toDateTimeAtStartOfDay() : null);
            encounter.setMaxVisitDateTime(maxDate != null ? maxDate.toDateTimeAtStartOfDay() : null);
        } else {
            String encounterDateStr = row.get(EncounterHeadersCreator.VISIT_DATE);
            if (encounterDateStr == null || encounterDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            }
            encounterDate = DateTimeUtil.parseFlexibleDate(encounterDateStr);
            if (encounterDate.isAfter(LocalDate.now())) {
                allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.VISIT_DATE));
            }
            encounter.setEncounterDateTime(encounterDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());
        }

        ValidationUtil.handleErrors(allErrorMsgs);

        Encounter savedEncounter;

        if (skipRuleExecution()) {
            EncounterHeadersCreator encounterHeadersCreator = new EncounterHeadersCreator(strategyFactory);
            TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, encounterHeadersCreator);
            encounter.setObservations(observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, FormType.Encounter, encounter.getObservations(), formMapping));
            savedEncounter = encounterService.save(encounter);
        } else {
            UploadRuleServerResponseContract ruleResponse = ruleServerInvoker.getRuleServerResult(row, formMapping.getForm(), encounter, allErrorMsgs);
            encounter.setObservations(observationService.createObservations(ruleResponse.getObservations()));
            decisionCreator.addEncounterDecisions(encounter.getObservations(), ruleResponse.getDecisions());
            decisionCreator.addRegistrationDecisions(null, ruleResponse.getDecisions());
            savedEncounter = encounterService.save(encounter);
            visitCreator.saveScheduledVisits(formMapping.getType(), null, null, ruleResponse.getVisitSchedules(), savedEncounter.getUuid());
        }
        entityApprovalStatusWriter.saveStatus(formMapping, savedEncounter.getId(), EntityApprovalStatus.EntityType.Encounter, savedEncounter.getEncounterType().getUuid());
    }

    private Encounter getOrCreateEncounter(Row row) {
        String id = row.get(EncounterHeadersCreator.ID);
        return createNewEncounter(id);
    }

    private Encounter createNewEncounter(String externalId) {
        Encounter encounter = new Encounter();
        encounter.setLegacyId(externalId);
        return encounter;
    }
}
