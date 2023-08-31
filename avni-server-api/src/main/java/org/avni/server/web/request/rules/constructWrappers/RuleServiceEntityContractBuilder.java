package org.avni.server.web.request.rules.constructWrappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.domain.*;
import org.avni.server.service.EntityApprovalStatusService;
import org.avni.server.service.ObservationService;
import org.avni.server.web.request.rules.RulesContractWrapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleServiceEntityContractBuilder {
    private ObservationService observationService;
    private EntityApprovalStatusService entityApprovalStatusService;
    private ProgramEncounterConstructionService programEncounterConstructionService;
    private IndividualConstructionService individualConstructionService;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public RuleServiceEntityContractBuilder(ObservationService observationService, EntityApprovalStatusService entityApprovalStatusService, ProgramEncounterConstructionService programEncounterConstructionService, IndividualConstructionService individualConstructionService) {
        this.observationService = observationService;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.programEncounterConstructionService = programEncounterConstructionService;
        this.individualConstructionService = individualConstructionService;
    }

    public ProgramEnrolmentContract toContract(ProgramEnrolment programEnrolment) {
        ProgramEnrolmentContract programEnrolmentContract = ProgramEnrolmentContract.fromEnrolment(programEnrolment, observationService, entityApprovalStatusService);
        Set<ProgramEncounterContract> programEncountersContracts = programEnrolment.getProgramEncounters().stream().map(programEncounterConstructionService::constructProgramEncounterContractWrapper).collect(Collectors.toSet());
        programEnrolmentContract.setProgramEncounters(programEncountersContracts);
        programEnrolmentContract.setSubject(individualConstructionService.getSubjectInfo(programEnrolment.getIndividual()));
        return programEnrolmentContract;
    }

    public IndividualContract toContract(Individual individual) {
        return individualConstructionService.getSubjectInfo(individual);
    }

    public ProgramEncounterContract toContract(ProgramEncounter programEncounter) {
        logObject(programEncounter, "toContract::programEncounter::%s");
        return programEncounterConstructionService.programEnrolmentWrapperForMessageSchedule(programEncounter);
    }

    public EncounterContract toContract(Encounter encounter) {
        EncounterContract encounterContract = EncounterContract.fromEncounter(encounter, observationService, entityApprovalStatusService);
        encounterContract.setSubject(individualConstructionService.getSubjectInfo(encounter.getIndividual()));
        return encounterContract;
    }

    private void logObject(Object observationContract, String infoString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonString = objectMapper.writeValueAsString(observationContract);
            logger.info(String.format(infoString, jsonString));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public RuleServerEntityContract toContract(String entityType, CHSEntity entity) {
        switch (entityType) {
            case "Subject":
                return toContract((Individual) entity);
            case "ProgramEnrolment":
                return toContract((ProgramEnrolment) entity);
            case "Encounter":
                return toContract((Encounter) entity);
            case "ProgramEncounter":
                return toContract((ProgramEncounter) entity);
            default:
                throw new IllegalArgumentException("Unknown entityType " + entityType);
        }
    }
}
