package org.avni.service;

import com.bugsnag.Bugsnag;
import org.avni.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.avni.dao.*;
import org.avni.dao.individualRelationship.RuleFailureLogRepository;
import org.avni.domain.*;
import org.avni.util.BadRequestError;
import org.avni.web.request.*;
import org.avni.web.request.rules.RulesContractWrapper.VisitSchedule;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.jpa.domain.Specifications.where;

@Service
public class EncounterService implements ScopeAwareService {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(EncounterService.class);
    @Autowired
    Bugsnag bugsnag;
    private EncounterRepository encounterRepository;
    private ObservationService observationService;
    private IndividualRepository individualRepository;
    private RuleFailureLogRepository ruleFailureLogRepository;
    private EncounterTypeRepository encounterTypeRepository;

    @Autowired
    public EncounterService(EncounterRepository encounterRepository, ObservationService observationService, IndividualRepository individualRepository, RuleFailureLogRepository ruleFailureLogRepository, EncounterTypeRepository encounterTypeRepository) {
        this.encounterRepository = encounterRepository;
        this.observationService = observationService;
        this.individualRepository = individualRepository;
        this.ruleFailureLogRepository = ruleFailureLogRepository;
        this.encounterTypeRepository = encounterTypeRepository;
    }

    public EncounterContract getEncounterByUuid(String uuid) {
        Encounter encounter = encounterRepository.findByUuid(uuid);
        return constructEncounters(encounter);
    }

    public Page<EncounterContract> getAllCompletedEncounters(String uuid, String encounterTypeUuids, DateTime encounterDateTime, DateTime earliestVisitDateTime, Pageable pageable){
        Page<EncounterContract> encountersContract = null;
        List<String> encounterTypeIdList = new ArrayList<>();
        if(encounterTypeUuids != null) {
            encounterTypeIdList = Arrays.asList(encounterTypeUuids.split(","));
        }
        Individual individual = individualRepository.findByUuid(uuid);
        Specification<Encounter> completedEncounterSpecification = Specification.where(encounterRepository.withNotNullEncounterDateTime())
                .or(encounterRepository.withNotNullCancelDateTime());
        encountersContract = encounterRepository.findAll(
                where(encounterRepository.withIndividualId(individual.getId()))
                        .and(encounterRepository.withEncounterTypeIdUuids(encounterTypeIdList))
                        .and(encounterRepository.withEncounterEarliestVisitDateTime(earliestVisitDateTime))
                        .and(encounterRepository.withEncounterDateTime(encounterDateTime))
                        .and(encounterRepository.withVoidedFalse())
                        .and(completedEncounterSpecification)
                ,pageable).map(encounter -> constructEncounters(encounter));
        return encountersContract;
    }

    public EncounterContract constructEncounters(Encounter encounter) {
            EncounterContract encountersContract = new EncounterContract();
            EncounterTypeContract encounterTypeContract = new EncounterTypeContract();
            encounterTypeContract.setName(encounter.getEncounterType().getName());
            encounterTypeContract.setUuid(encounter.getEncounterType().getUuid());
            encounterTypeContract.setEncounterEligibilityCheckRule(encounter.getEncounterType().getEncounterEligibilityCheckRule());
            encountersContract.setUuid(encounter.getUuid());
            encountersContract.setName(encounter.getName());
            encountersContract.setEncounterType(encounterTypeContract);
            encountersContract.setSubjectUUID(encounter.getIndividual().getUuid());
            encountersContract.setEncounterDateTime(encounter.getEncounterDateTime());
            encountersContract.setCancelDateTime(encounter.getCancelDateTime());
            encountersContract.setEarliestVisitDateTime(encounter.getEarliestVisitDateTime());
            encountersContract.setMaxVisitDateTime(encounter.getMaxVisitDateTime());
            encountersContract.setVoided(encounter.isVoided());
            if(encounter.getObservations() != null) {
                encountersContract.setObservations(observationService.constructObservations(encounter.getObservations()));
            }
            if(encounter.getCancelObservations() != null) {
                encountersContract.setCancelObservations(observationService.constructObservations(encounter.getCancelObservations()));
            }
        return  encountersContract;
    }

    public List<Encounter> scheduledEncountersByType(Individual individual, String encounterTypeName, String currentEncounterUuid) {
        Stream<Encounter> scheduledEncounters = individual.scheduledEncountersOfType(encounterTypeName).filter(enc -> !enc.getUuid().equals(currentEncounterUuid));
        return scheduledEncounters.collect(Collectors.toList());
    }

    public void saveVisitSchedules(String individualUuid, List<VisitSchedule> visitSchedules, String currentEncounterUuid) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        for (VisitSchedule visitSchedule : visitSchedules) {
            saveVisitSchedule(individual, visitSchedule, currentEncounterUuid);
        }
    }

    public void saveVisitSchedule(Individual individual, VisitSchedule visitSchedule, String currentEncounterUuid) {
        List<Encounter> allScheduleEncountersByType = scheduledEncountersByType(individual, visitSchedule.getEncounterType(), currentEncounterUuid);
        if (allScheduleEncountersByType.isEmpty() || "createNew".equals(visitSchedule.getVisitCreationStrategy())) {
            EncounterType encounterType = encounterTypeRepository.findByName(visitSchedule.getEncounterType());
            if (encounterType == null) {
                throw new BadRequestError("Next scheduled visit is for encounter type=%s that doesn't exist", visitSchedule.getName());
            }
            Encounter encounter = createEmptyEncounter(individual, encounterType);
            allScheduleEncountersByType = Arrays.asList(encounter);
        }
        allScheduleEncountersByType.stream().forEach(encounter -> {
            updateEncounterWithVisitSchedule(encounter, visitSchedule);
            encounterRepository.save(encounter);
        });
    }

    public void updateEncounterWithVisitSchedule(Encounter encounter, VisitSchedule visitSchedule) {
        encounter.setEarliestVisitDateTime(visitSchedule.getEarliestDate());
        encounter.setMaxVisitDateTime(visitSchedule.getMaxDate());
        encounter.setName(visitSchedule.getName());
    }

    public Encounter createEmptyEncounter(Individual individual, EncounterType encounterType) {
        Encounter encounter = new Encounter();
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(individual);
        encounter.setEncounterDateTime(null);
        encounter.setUuid(UUID.randomUUID().toString());
        encounter.setVoided(false);
        encounter.setObservations(new ObservationCollection());
        encounter.setCancelObservations(new ObservationCollection());
        return encounter;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String encounterTypeUuid) {
        return true;
//        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUuid);
//        User user = UserContextHolder.getUserContext().getUser();
//        return encounterType != null &&  isChanged(user, lastModifiedDateTime, encounterType.getId());
    }

    @Override
    public OperatingIndividualScopeAwareRepository repository() {
        return encounterRepository;
    }
}
