package org.avni.server.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.domain.EntityType;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.EntityHelper;
import org.avni.server.common.Messageable;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.geo.Point;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.EntityTypeContract;
import org.avni.server.web.request.PointRequest;
import org.avni.server.web.request.ProgramEncounterRequest;
import org.avni.server.web.request.ProgramEncounterContract;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.avni.server.web.request.rules.RulesContractWrapper.VisitSchedule;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProgramEncounterService implements ScopeAwareService {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProgramEncounterService.class);
    @Autowired
    Bugsnag bugsnag;
    private final ProgramEncounterRepository programEncounterRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    private final ObservationService observationService;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final FormMappingService formMappingService;

    @Autowired
    public ProgramEncounterService(ProgramEncounterRepository programEncounterRepository, EncounterTypeRepository encounterTypeRepository, OperationalEncounterTypeRepository operationalEncounterTypeRepository, ObservationService observationService, ProgramEnrolmentRepository programEnrolmentRepository, FormMappingService formMappingService) {
        this.programEncounterRepository = programEncounterRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
        this.observationService = observationService;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.formMappingService = formMappingService;
    }

    public ProgramEncounterContract getProgramEncounterByUuid(String uuid) {
        ProgramEncounter programEncounter = programEncounterRepository.findByUuid(uuid);
        return constructProgramEncounters(programEncounter);
    }

    public ProgramEncounterContract constructProgramEncounters(ProgramEncounter programEncounter) {
        ProgramEncounterContract programEncountersContract = new ProgramEncounterContract();
        EntityTypeContract entityTypeContract = new EntityTypeContract();
        entityTypeContract.setName(programEncounter.getEncounterType().getName());
        entityTypeContract.setUuid(programEncounter.getEncounterType().getUuid());
        entityTypeContract.setEntityEligibilityCheckRule(programEncounter.getEncounterType().getEncounterEligibilityCheckRule());
        entityTypeContract.setImmutable(programEncounter.getEncounterType().isImmutable());
        programEncountersContract.setUuid(programEncounter.getUuid());
        programEncountersContract.setName(programEncounter.getName());
        programEncountersContract.setEncounterType(entityTypeContract);
        programEncountersContract.setSubjectUUID(programEncounter.getProgramEnrolment().getIndividual().getUuid());
        programEncountersContract.setEncounterDateTime(programEncounter.getEncounterDateTime());
        programEncountersContract.setCancelDateTime(programEncounter.getCancelDateTime());
        programEncountersContract.setEarliestVisitDateTime(programEncounter.getEarliestVisitDateTime());
        programEncountersContract.setMaxVisitDateTime(programEncounter.getMaxVisitDateTime());
        programEncountersContract.setVoided(programEncounter.isVoided());
        programEncountersContract.setEnrolmentUUID(programEncounter.getProgramEnrolment().getUuid());
        if (programEncounter.getObservations() != null) {
            programEncountersContract.setObservations(observationService.constructObservations(programEncounter.getObservations()));
        }
        if (programEncounter.getCancelObservations() != null) {
            programEncountersContract.setCancelObservations(observationService.constructObservations(programEncounter.getCancelObservations()));
        }
        return programEncountersContract;
    }

    public List<ProgramEncounter> scheduledEncountersByType(ProgramEnrolment programEnrolment, String encounterTypeName, String currentProgramEncounterUuid) {
        Stream<ProgramEncounter> scheduledEncounters = programEnrolment.scheduledEncountersOfType(encounterTypeName).filter(enc -> !enc.getUuid().equals(currentProgramEncounterUuid));
        return scheduledEncounters.collect(Collectors.toList());
    }

    public void saveVisitSchedules(String programEnrolmentUuid, List<VisitSchedule> visitSchedules, String currentProgramEncounterUuid) {
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(programEnrolmentUuid);
        for (VisitSchedule visitSchedule : visitSchedules) {
            saveVisitSchedule(programEnrolment, visitSchedule, currentProgramEncounterUuid);
        }
    }

    public void saveVisitSchedule(ProgramEnrolment programEnrolment, VisitSchedule visitSchedule, String currentProgramEncounterUuid) {
        List<ProgramEncounter> allScheduleEncountersByType = scheduledEncountersByType(programEnrolment, visitSchedule.getEncounterType(), currentProgramEncounterUuid);
        if (allScheduleEncountersByType.isEmpty() || "createNew".equals(visitSchedule.getVisitCreationStrategy())) {
            OperationalEncounterType operationalEncounterType = operationalEncounterTypeRepository.findByName(visitSchedule.getEncounterType());
            if (operationalEncounterType == null) {
                EncounterType encounterType = encounterTypeRepository.findByName(visitSchedule.getEncounterType());
                if (encounterType == null) {
                    throw new BadRequestError("Next scheduled visit is for encounter type=%s that doesn't exist", visitSchedule.getEncounterType());
                } else {
                    operationalEncounterType = encounterType.getOperationalEncounterTypes().stream().findFirst().orElse(null);
                    if (operationalEncounterType == null) {
                        throw new BadRequestError("Next scheduled visit is for encounter type=%s that doesn't exist", visitSchedule.getEncounterType());
                    }
                }
            }
            ProgramEncounter programEncounter = createEmptyProgramEncounter(programEnrolment, operationalEncounterType);
            allScheduleEncountersByType = Arrays.asList(programEncounter);
        }
        allScheduleEncountersByType.stream().forEach(programEncounter -> {
            updateProgramEncounterWithVisitSchedule(programEncounter, visitSchedule);
            programEncounter.setProgramEnrolment(programEnrolment);
            this.save(programEncounter);
        });
    }

    public void updateProgramEncounterWithVisitSchedule(ProgramEncounter programEncounter, VisitSchedule visitSchedule) {
        programEncounter.setEarliestVisitDateTime(visitSchedule.getEarliestDate());
        programEncounter.setMaxVisitDateTime(visitSchedule.getMaxDate());
        programEncounter.setName(visitSchedule.getName());
    }

    public ProgramEncounter createEmptyProgramEncounter(ProgramEnrolment programEnrolment, OperationalEncounterType operationalEncounterType) {
        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.setEncounterType(operationalEncounterType.getEncounterType());
        programEncounter.setProgramEnrolment(programEnrolment);
        programEncounter.setEncounterDateTime(null);
        programEncounter.setUuid(UUID.randomUUID().toString());
        programEncounter.setVoided(false);
        programEncounter.setObservations(new ObservationCollection());
        programEncounter.setCancelObservations(new ObservationCollection());
        return programEncounter;
    }

    @Messageable(EntityType.ProgramEncounter)
    public ProgramEncounter saveProgramEncounter(ProgramEncounterRequest request) {
        logger.info(String.format("Saving programEncounter with uuid %s", request.getUuid()));
        checkForSchedulingCompleteConstraintViolation(request);
        EncounterType encounterType = encounterTypeRepository.findByUuidOrName(request.getEncounterType(), request.getEncounterTypeUUID());
        Decisions decisions = request.getDecisions();
        observationService.validateObservationsAndDecisions(request.getObservations(), decisions != null ? decisions.getEncounterDecisions() : null, formMappingService.find(encounterType, FormType.ProgramEncounter));
        ProgramEncounter encounter = EntityHelper.newOrExistingEntity(programEncounterRepository, request, new ProgramEncounter());
        //Planned visit can not overwrite completed encounter
        if (encounter.isCompleted() && request.isPlanned())
            return null;

        encounter.setEncounterDateTime(request.getEncounterDateTime());
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(request.getProgramEnrolmentUUID());
        encounter.setProgramEnrolment(programEnrolment);
        encounter.setEncounterType(encounterType);
        encounter.setObservations(observationService.createObservations(request.getObservations()));
        encounter.setName(request.getName());
        encounter.setEarliestVisitDateTime(request.getEarliestVisitDateTime());
        encounter.setMaxVisitDateTime(request.getMaxVisitDateTime());
        encounter.setCancelDateTime(request.getCancelDateTime());
        encounter.setCancelObservations(observationService.createObservations(request.getCancelObservations()));
        PointRequest encounterLocation = request.getEncounterLocation();
        if (encounterLocation != null)
            encounter.setEncounterLocation(new Point(encounterLocation.getX(), encounterLocation.getY()));
        PointRequest cancelLocation = request.getCancelLocation();
        if (cancelLocation != null)
            encounter.setCancelLocation(new Point(cancelLocation.getX(), cancelLocation.getY()));

        if (decisions != null) {
            ObservationCollection observationsFromDecisions = observationService
                    .createObservationsFromDecisions(decisions.getEncounterDecisions());
            if (decisions.isCancel()) {
                encounter.getCancelObservations().putAll(observationsFromDecisions);
            } else {
                encounter.getObservations().putAll(observationsFromDecisions);
            }

            List<Decision> enrolmentDecisions = decisions.getEnrolmentDecisions();
            if (enrolmentDecisions != null) {
                ObservationCollection enrolmentObservations = observationService.createObservationsFromDecisions(enrolmentDecisions);
                programEnrolment.getObservations().putAll(enrolmentObservations);
            }
            List<Decision> registrationDecisions = decisions.getRegistrationDecisions();
            if (registrationDecisions != null) {
                ObservationCollection registrationObservations = observationService.createObservationsFromDecisions(registrationDecisions);
                programEnrolment.getIndividual().addObservations(registrationObservations);
            }
        }
        encounter = this.save(encounter);
        logger.info(String.format("Saved programEncounter with uuid %s", request.getUuid()));
        return encounter;
    }

    private void checkForSchedulingCompleteConstraintViolation(ProgramEncounterRequest request) {
        if ((request.getEarliestVisitDateTime() != null || request.getMaxVisitDateTime() != null)
                && (request.getEarliestVisitDateTime() == null || request.getMaxVisitDateTime() == null)
        ) {
            //violating constraint so notify bugsnag
            bugsnag.notify(new Exception(String.format("ProgramEncounter violating scheduling constraint uuid %s earliest %s max %s", request.getUuid(), request.getEarliestVisitDateTime(), request.getMaxVisitDateTime())));
        }
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String encounterTypeUUID) {
        return true;
    }

    @Override
    public OperatingIndividualScopeAwareRepository repository() {
        return programEncounterRepository;
    }

    @Messageable(EntityType.ProgramEncounter)
    public ProgramEncounter save(ProgramEncounter programEncounter) {
        ProgramEnrolment programEnrolment = programEncounter.getProgramEnrolment();
        Individual individual = programEnrolment.getIndividual();
        programEncounter.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        programEncounter.setIndividual(individual);
        if (individual.getAddressLevel() != null) {
            programEncounter.setAddressId(individual.getAddressLevel().getId());
        }
        programEncounter = programEncounterRepository.save(programEncounter);

        return programEncounter;
    }

    public FormMapping getFormMapping(ProgramEncounter programEncounter) {
        SubjectType subjectType = programEncounter.getIndividual().getSubjectType();
        Program program = programEncounter.getProgramEnrolment().getProgram();
        return formMappingService.findBy(subjectType, program, programEncounter.getEncounterType(), getFormType(programEncounter));
    }

    private static FormType getFormType(ProgramEncounter programEncounter) {
        return programEncounter.isCancelled() ? FormType.ProgramEncounterCancellation : FormType.ProgramEncounter;
    }
}
