package org.avni.server.service;

import org.avni.messaging.domain.EntityType;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.EntityHelper;
import org.avni.server.common.Messageable;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.geo.Point;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.*;
import org.avni.server.web.request.rules.RulesContractWrapper.ChecklistContract;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specification.where;


@Service
public class ProgramEnrolmentService implements ScopeAwareService<ProgramEnrolment> {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(ProgramEnrolmentService.class);

    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final ProgramEncounterService programEncounterService;
    private final ProgramEncounterRepository programEncounterRepository;
    private final ProgramRepository programRepository;
    private final ObservationService observationService;
    private final IndividualRepository individualRepository;
    private final ProgramOutcomeRepository programOutcomeRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final ChecklistItemDetailRepository checklistItemDetailRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final IdentifierAssignmentRepository identifierAssignmentRepository;
    private final AccessControlService accessControlService;
    private final FormMappingService formMappingService;

    @Autowired
    public ProgramEnrolmentService(ProgramEnrolmentRepository programEnrolmentRepository,
                                   ProgramEncounterService programEncounterService,
                                   ProgramEncounterRepository programEncounterRepository,
                                   ProgramRepository programRepository,
                                   ObservationService observationService,
                                   IndividualRepository individualRepository,
                                   ProgramOutcomeRepository programOutcomeRepository,
                                   ChecklistDetailRepository checklistDetailRepository,
                                   ChecklistItemDetailRepository checklistItemDetailRepository,
                                   ChecklistRepository checklistRepository,
                                   ChecklistItemRepository checklistItemRepository,
                                   IdentifierAssignmentRepository identifierAssignmentRepository,
                                   AccessControlService accessControlService,
                                   FormMappingService formMappingService) {
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.programEncounterService = programEncounterService;
        this.programEncounterRepository = programEncounterRepository;
        this.programRepository = programRepository;
        this.observationService = observationService;
        this.individualRepository = individualRepository;
        this.programOutcomeRepository = programOutcomeRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.checklistItemDetailRepository = checklistItemDetailRepository;
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.formMappingService = formMappingService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public ProgramEncounter matchingEncounter(String programEnrolmentUUID, String encounterTypeName, DateTime encounterDateTime) {
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(programEnrolmentUUID);
        if (programEnrolment == null) {
            throw new IllegalArgumentException(String.format("ProgramEnrolment not found with UUID '%s'", programEnrolmentUUID));
        }
        return programEnrolment.getProgramEncounters().stream()
                .filter(programEncounter ->
                        programEncounter.getEncounterType().getName().equals(encounterTypeName)
                                && programEncounter.dateFallsWithIn(encounterDateTime))
                .findAny()
                .orElse(null);
    }

    public EnrolmentContract constructEnrolments(String uuid) {
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(uuid);
        EnrolmentContract enrolmentContract = new EnrolmentContract();
        enrolmentContract.setUuid(programEnrolment.getUuid());
        enrolmentContract.setProgramUuid(programEnrolment.getProgram().getUuid());
        enrolmentContract.setOperationalProgramName(programEnrolment.getProgram().getOperationalProgramName());
        enrolmentContract.setEnrolmentDateTime(programEnrolment.getEnrolmentDateTime());
        enrolmentContract.setProgramExitDateTime(programEnrolment.getProgramExitDateTime());
        enrolmentContract.setSubjectUuid(programEnrolment.getIndividual().getUuid());
        enrolmentContract.setVoided(programEnrolment.isVoided());
        Set<ProgramEncounterContract> programEncounters = programEnrolment.nonVoidedEncounters()
                .map(programEncounterService::constructProgramEncounters)
                .collect(Collectors.toSet());
        enrolmentContract.setProgramEncounters(programEncounters);
        List<ObservationContract> observationContractsList = observationService.constructObservations(programEnrolment.getObservations());
        enrolmentContract.setObservations(observationContractsList);
        if (programEnrolment.getProgramExitObservations() != null) {
            enrolmentContract.setExitObservations(observationService.constructObservations(programEnrolment.getProgramExitObservations()));
        }
        return enrolmentContract;
    }

    public Page<ProgramEncounterContract> getAllCompletedEncounters(String uuid, String encounterTypeUuids, DateTime encounterDateTime, DateTime earliestVisitDateTime, Pageable pageable){
        Page<ProgramEncounterContract> programEncountersContract;
        List<String> encounterTypeIdList = new ArrayList<>();
        if(encounterTypeUuids != null) {
            encounterTypeIdList = Arrays.asList(encounterTypeUuids.split(","));
        }
        List<String> accessibleEncounterTypeUUIDs = encounterTypeIdList.stream().filter(accessControlService::hasProgramEncounterPrivilege).collect(Collectors.toList());
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(uuid);
        Specification<ProgramEncounter> completedEncounterSpecification = where(programEncounterRepository.withNotNullEncounterDateTime())
                .or(programEncounterRepository.withNotNullCancelDateTime())
                .and(programEncounterRepository.withVoidedFalse());
        programEncountersContract = programEncounterRepository.findAll(
                where(programEncounterRepository.withProgramEncounterId(programEnrolment.getId()))
                        .and(programEncounterRepository.withProgramEncounterTypeIdUuids(accessibleEncounterTypeUUIDs))
                        .and(programEncounterRepository.withProgramEncounterEarliestVisitDateTime(earliestVisitDateTime))
                        .and(programEncounterRepository.withProgramEncounterDateTime(encounterDateTime))
                        .and(completedEncounterSpecification)
                ,pageable).map(programEncounterService::constructProgramEncounters);
        return programEncountersContract;
    }

    @Messageable(EntityType.ProgramEnrolment)
    public ProgramEnrolment programEnrolmentSave(ProgramEnrolmentRequest request){
        logger.info(String.format("Saving programEnrolment with uuid %s", request.getUuid()));
        Program program;
        if (request.getProgramUUID() == null) {
            program = programRepository.findByName(request.getProgram());
        } else {
            program = programRepository.findByUuid(request.getProgramUUID());
        }
        ProgramOutcome programOutcome = programOutcomeRepository.findByUuid(request.getProgramOutcomeUUID());
        ProgramEnrolment programEnrolment = EntityHelper.newOrExistingEntity(programEnrolmentRepository,request, new ProgramEnrolment());
        programEnrolment.setProgram(program);
        programEnrolment.setProgramOutcome(programOutcome);
        programEnrolment.setEnrolmentDateTime(request.getEnrolmentDateTime());
        programEnrolment.setProgramExitDateTime(request.getProgramExitDateTime());
        PointRequest enrolmentLocation = request.getEnrolmentLocation();
        if (enrolmentLocation != null)
            programEnrolment.setEnrolmentLocation(new Point(enrolmentLocation.getX(), enrolmentLocation.getY()));
        PointRequest exitLocation = request.getExitLocation();
        if (exitLocation != null)
            programEnrolment.setExitLocation(new Point(exitLocation.getX(), exitLocation.getY()));
        programEnrolment.setObservations(observationService.createObservations(request.getObservations()));
        programEnrolment.setProgramExitObservations(observationService.createObservations(request.getProgramExitObservations()));

        Decisions decisions = request.getDecisions();
        if(decisions != null) {
            ObservationCollection observationsFromDecisions = observationService
                    .createObservationsFromDecisions(decisions.getEnrolmentDecisions());
            if(decisions.isExit()) {
                programEnrolment.getProgramExitObservations().putAll(observationsFromDecisions);
            } else {
                programEnrolment.getObservations().putAll(observationsFromDecisions);
            }
        }

        Individual individual = individualRepository.findByUuid(request.getIndividualUUID());
        programEnrolment.setIndividual(individual);
        this.addSyncAttributes(programEnrolment);
        if (programEnrolment.isNew()) {
            programEnrolment.setIndividual(individual);
            saveIdentifierAssignments(programEnrolment, request);
        }
        programEnrolment = programEnrolmentRepository.save(programEnrolment);

        if (request.getVisitSchedules() != null && request.getVisitSchedules().size() > 0) {
            programEncounterService.saveVisitSchedules(request.getUuid(), request.getVisitSchedules(), null);
        }
        if (request.getChecklists() != null && request.getChecklists().size() > 0) {
            request.getChecklists()
                    .forEach(checklist -> saveChecklist(checklist, request.getUuid()));
        }

        logger.info(String.format("Saved programEnrolment with uuid %s", request.getUuid()));
        return programEnrolment;
    }

    @Messageable(EntityType.ProgramEnrolment)
    public ProgramEnrolment save(ProgramEnrolment programEnrolment) {
        this.addSyncAttributes(programEnrolment);
       return programEnrolmentRepository.save(programEnrolment);
    }

    private void addSyncAttributes(ProgramEnrolment enrolment) {
        Individual individual = enrolment.getIndividual();
        enrolment.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        if (individual.getAddressLevel() != null) {
            enrolment.setAddressId(individual.getAddressLevel().getId());
        }
    }

    private void saveIdentifierAssignments(ProgramEnrolment programEnrolment, ProgramEnrolmentRequest programEnrolmentRequest) {
        List<String> identifierAssignmentUuids = programEnrolmentRequest.getIdentifierAssignmentUuids();
        if(identifierAssignmentUuids != null) {
            identifierAssignmentUuids.forEach(uuid -> {
                IdentifierAssignment identifierAssignment = identifierAssignmentRepository.findByUuid(uuid);
                identifierAssignment.setProgramEnrolment(programEnrolment);
                identifierAssignmentRepository.save(identifierAssignment);
            });
        }
    }

    private Checklist saveChecklist(ChecklistContract checklistContract, String programEnrolmentUUID){
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(programEnrolmentUUID);
        Checklist existingChecklist = checklistRepository.findByProgramEnrolmentId(programEnrolment.getId());
        if (existingChecklist != null) {
            existingChecklist.setBaseDate(checklistContract.getBaseDate());
            return checklistRepository.save(existingChecklist);
        }
        Checklist checklist = new Checklist();
        checklist.assignUUIDIfRequired();
        String checklistDetailUUID = checklistContract.getDetail().getUuid();
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUUID);
        checklist.setBaseDate(checklistContract.getBaseDate());
        checklist.setChecklistDetail(checklistDetail);
        checklist.setProgramEnrolment(programEnrolment);
        Checklist savedChecklist = checklistRepository.save(checklist);
        checklistContract.getItems().forEach(item -> {
            ChecklistItem checklistItem = new ChecklistItem();
            checklistItem.assignUUIDIfRequired();
            String checklistItemDetailUUID = item.getDetail().getUuid();
            ChecklistItemDetail checklistItemDetail = checklistItemDetailRepository.findByUuid(checklistItemDetailUUID);
            checklistItem.setChecklistItemDetail(checklistItemDetail);
            checklistItem.setChecklist(savedChecklist);
            checklistItemRepository.save(checklistItem);
        });
        return savedChecklist;
    }

    @Messageable(EntityType.ProgramEnrolment)
    public ProgramEnrolment voidEnrolment(ProgramEnrolment programEnrolment) {
        assertNoUnVoidedProgramEncounters(programEnrolment);
        programEnrolment.setVoided(true);
        return programEnrolmentRepository.save(programEnrolment);
    }

    private void assertNoUnVoidedProgramEncounters(ProgramEnrolment programEnrolment) {
        long unVoidedProgramEncounters = programEnrolment.nonVoidedEncounters().count();
        if (unVoidedProgramEncounters != 0) {
            String programName = programEnrolment.getProgram().getName();
            throw new BadRequestError(String.format("There are non deleted program encounters for the program %s", programName));
        }
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String programUUID) {
        return true;
//        Program program = programRepository.findByUuid(programUUID);
//        FormMapping formMapping = formMappingRepository.getAllProgramEnrolmentFormMappings()
//                .stream()
//                .filter(fm -> fm.getProgramUuid().equals(programUUID))
//                .findFirst()
//                .orElse(null);
//        User user = UserContextHolder.getUserContext().getUser();
//        return program != null &&
//                formMapping != null &&
//                isChanged(user, lastModifiedDateTime, program.getId(), formMapping.getSubjectType());
    }

    @Override
    public OperatingIndividualScopeAwareRepository<ProgramEnrolment> repository() {
        return programEnrolmentRepository;
    }

    public FormMapping getFormMapping(ProgramEnrolment programEnrolment) {
        FormType formType = programEnrolment.isExited() ?  FormType.ProgramExit : FormType.ProgramEnrolment;
        return formMappingService.findBy(programEnrolment.getIndividual().getSubjectType(),programEnrolment.getProgram(), null , formType);
    }
}
