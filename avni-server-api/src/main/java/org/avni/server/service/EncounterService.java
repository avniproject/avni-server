package org.avni.server.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.domain.EntityType;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.Messageable;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.geo.Point;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.S;
import org.avni.server.web.api.CommonFieldNames;
import org.avni.server.web.api.EncounterSearchRequest;
import org.avni.server.web.request.EncounterContract;
import org.avni.server.web.request.EntityTypeContract;
import org.avni.server.web.request.api.RequestUtils;
import org.avni.server.web.request.rules.RulesContractWrapper.VisitSchedule;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.avni.server.web.request.api.ApiBaseEncounterRequest.*;
import static org.avni.server.web.request.api.ApiSubjectRequest.OBSERVATIONS;
import static org.springframework.data.jpa.domain.Specification.where;

@Service
public class EncounterService implements ScopeAwareService<Encounter> {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(EncounterService.class);
    @Autowired
    Bugsnag bugsnag;
    private final EncounterRepository encounterRepository;
    private final ObservationService observationService;
    private final IndividualRepository individualRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final EncounterSearchRepository encounterSearchRepository;
    private final FormMappingService formMappingService;
    private final AccessControlService accessControlService;
    private final ConceptRepository conceptRepository;
    private final MediaObservationService mediaObservationService;
    private final UserService userService;

    @Autowired
    public EncounterService(EncounterRepository encounterRepository, ObservationService observationService, IndividualRepository individualRepository, EncounterTypeRepository encounterTypeRepository, EncounterSearchRepository encounterSearchRepository, AccessControlService accessControlService
            , FormMappingService formMappingService, ConceptRepository conceptRepository, MediaObservationService mediaObservationService, UserService userService) {
        this.encounterRepository = encounterRepository;
        this.observationService = observationService;
        this.individualRepository = individualRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.encounterSearchRepository = encounterSearchRepository;
        this.formMappingService = formMappingService;
        this.accessControlService = accessControlService;
        this.conceptRepository = conceptRepository;
        this.mediaObservationService = mediaObservationService;
        this.userService = userService;
    }

    public EncounterContract getEncounterByUuid(String uuid) {
        Encounter encounter = encounterRepository.findByUuid(uuid);
        return constructEncounter(encounter);
    }

    public Page<EncounterContract> getAllCompletedEncounters(String uuid, String encounterTypeUuids, DateTime encounterDateTime, DateTime earliestVisitDateTime, Pageable pageable) {
        Page<EncounterContract> encountersContract;
        List<String> encounterTypeIdList = new ArrayList<>();
        if(encounterTypeUuids != null) {
            encounterTypeIdList = Arrays.asList(encounterTypeUuids.split(","));
        }
        Individual individual = individualRepository.findByUuid(uuid);
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, individual);
        Specification<Encounter> completedEncounterSpecification = Specification.where(encounterRepository.withNotNullEncounterDateTime())
                .or(encounterRepository.withNotNullCancelDateTime());
        encountersContract = encounterRepository.findAll(
                where(encounterRepository.withIndividualId(individual.getId()))
                        .and(encounterRepository.withEncounterTypeIdUuids(encounterTypeIdList))
                        .and(encounterRepository.withEncounterEarliestVisitDateTime(earliestVisitDateTime))
                        .and(encounterRepository.withEncounterDateTime(encounterDateTime))
                        .and(encounterRepository.withVoidedFalse())
                        .and(completedEncounterSpecification)
                , pageable).map(this::constructEncounter);
        return encountersContract;
    }

    public EncounterContract constructEncounter(Encounter encounter) {
        accessControlService.checkEncounterPrivilege(PrivilegeType.ViewVisit, encounter);
        EncounterContract encounterContract = new EncounterContract();
        EntityTypeContract entityTypeContract = new EntityTypeContract();
        entityTypeContract.setName(encounter.getEncounterType().getName());
        entityTypeContract.setUuid(encounter.getEncounterType().getUuid());
        entityTypeContract.setEntityEligibilityCheckRule(encounter.getEncounterType().getEncounterEligibilityCheckRule());
        entityTypeContract.setImmutable(encounter.getEncounterType().isImmutable());
        encounterContract.setUuid(encounter.getUuid());
        encounterContract.setName(encounter.getName());
        encounterContract.setEncounterType(entityTypeContract);
        encounterContract.setSubjectUUID(encounter.getIndividual().getUuid());
        encounterContract.setEncounterDateTime(encounter.getEncounterDateTime());
        encounterContract.setCancelDateTime(encounter.getCancelDateTime());
        encounterContract.setEarliestVisitDateTime(encounter.getEarliestVisitDateTime());
        encounterContract.setMaxVisitDateTime(encounter.getMaxVisitDateTime());
        encounterContract.setVoided(encounter.isVoided());
        if (encounter.getObservations() != null) {
            encounterContract.setObservations(observationService.constructObservations(encounter.getObservations()));
        }
        if (encounter.getCancelObservations() != null) {
            encounterContract.setCancelObservations(observationService.constructObservations(encounter.getCancelObservations()));
        }
        return encounterContract;
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
            encounter.setIndividual(individual);
            this.save(encounter);
        });
    }

    public void updateEncounterWithVisitSchedule(Encounter encounter, VisitSchedule visitSchedule) {
        encounter.setEarliestVisitDateTime(visitSchedule.getEarliestDate());
        encounter.setMaxVisitDateTime(visitSchedule.getMaxDate());
        encounter.setName(visitSchedule.getName());
        encounter.setLastModifiedBy(userService.getCurrentUser());
    }

    public Encounter createEmptyEncounter(Individual individual, EncounterType encounterType) {
        Encounter encounter = new Encounter();
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(individual);
        encounter.setUuid(UUID.randomUUID().toString());
        encounter.setVoided(false);
        encounter.setObservations(new ObservationCollection());
        encounter.setCancelObservations(new ObservationCollection());
        encounter.setFilledBy(userService.getCurrentUser());
        encounter.setCreatedBy(userService.getCurrentUser());
        encounter.setLastModifiedBy(userService.getCurrentUser());
        return encounter;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String encounterTypeUuid) {
        return true;
    }

    @Override
    public OperatingIndividualScopeAwareRepository<Encounter> repository() {
        return encounterRepository;
    }

    @Messageable(EntityType.Encounter)
    public Encounter save(Encounter encounter) {
        Individual individual = encounter.getIndividual();
        encounter.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        if (individual.getAddressLevel() != null) {
            encounter.setAddressId(individual.getAddressLevel().getId());
        }
        return encounterRepository.saveEntity(encounter);
    }

    public Page<Encounter> search(EncounterSearchRequest encounterSearchRequest) {
        List<Encounter> results;
        //Use sql when concepts are required.
        if (!encounterSearchRequest.getConceptsMap().isEmpty()) {
            results = encounterSearchRepository.search(encounterSearchRequest);
            long total = encounterSearchRepository.getCount(encounterSearchRequest);
            return new PageImpl<>(results, encounterSearchRequest.getPageable(), total);
        }

        if (S.isEmpty(encounterSearchRequest.getEncounterType())) {
            return encounterRepository.findByConcepts(encounterSearchRequest.getLastModifiedDateTime(), encounterSearchRequest.getNow(), encounterSearchRequest.getConceptsMap(), encounterSearchRequest.getPageable());
        } else if (S.isEmpty(encounterSearchRequest.getSubjectUUID())) {
            return encounterRepository.findByConceptsAndEncounterType(encounterSearchRequest.getLastModifiedDateTime(), encounterSearchRequest.getNow(), encounterSearchRequest.getConceptsMap(), encounterSearchRequest.getEncounterType(), encounterSearchRequest.getPageable());
        } else {
            return encounterRepository.findByConceptsAndEncounterTypeAndSubject(encounterSearchRequest.getLastModifiedDateTime(), encounterSearchRequest.getNow(), encounterSearchRequest.getConceptsMap(), encounterSearchRequest.getEncounterType(), encounterSearchRequest.getSubjectUUID(), encounterSearchRequest.getPageable());
        }
    }

    public FormMapping getFormMapping(Encounter encounter) {
        FormType formType = encounter.isCancelled() ? FormType.IndividualEncounterCancellation : FormType.Encounter;
        return formMappingService.findBy(encounter.getIndividual().getSubjectType(), null, encounter.getEncounterType(), formType);
    }

    public Encounter patchEncounter(Encounter encounter, Map<String, Object> request) throws ValidationException, IOException {
        if (request.containsKey(ENCOUNTER_TYPE)) {
            String encounterTypeName = (String) request.get(ENCOUNTER_TYPE);
            EncounterType encounterType = encounterTypeRepository.findByName(encounterTypeName);
            encounter.setEncounterType(encounterType);
        }

        String externalId = (String) request.get(EXTERNAL_ID);

        if (StringUtils.hasLength(externalId)) {
            encounter.setLegacyId(externalId.trim());
        }

        if (request.containsKey(ENCOUNTER_LOCATION))
            encounter.setEncounterLocation(Point.fromMap((Map<String, Double>) request.get(ENCOUNTER_LOCATION)));

        if (request.containsKey(CANCEL_LOCATION))
            encounter.setCancelLocation(Point.fromMap((Map<String, Double>) request.get(CANCEL_LOCATION)));

        if (request.containsKey(ENCOUNTER_DATE_TIME))
            encounter.setEncounterDateTime(DateTimeUtil.parseNullableDateTime((String) request.get(ENCOUNTER_DATE_TIME)), userService.getCurrentUser());

        if (request.containsKey(EARLIEST_SCHEDULED_DATE))
            encounter.setEarliestVisitDateTime(DateTimeUtil.parseNullableDateTime((String) request.get(EARLIEST_SCHEDULED_DATE)));

        if (request.containsKey(MAX_SCHEDULED_DATE)) {
            encounter.setMaxVisitDateTime(DateTimeUtil.parseNullableDateTime((String) request.get(MAX_SCHEDULED_DATE)));
        }

        if (request.containsKey(CANCEL_DATE_TIME))
            encounter.setCancelDateTime(DateTimeUtil.parseNullableDateTime((String) request.get(CANCEL_DATE_TIME)));


        if (request.containsKey(OBSERVATIONS)) {
            Map<String, Object> observationsFromRequest = (Map<String, Object>) request.get(OBSERVATIONS);
            RequestUtils.patchObservations(observationsFromRequest, conceptRepository, encounter.getObservations());
        }

        if (request.containsKey(CANCEL_OBSERVATIONS)) {
            Map<String, Object> cancelObservationsFromRequest = (Map<String, Object>) request.get(CANCEL_OBSERVATIONS);
            RequestUtils.patchObservations(cancelObservationsFromRequest, conceptRepository, encounter.getCancelObservations());
        }

        if (request.containsKey(CommonFieldNames.VOIDED))
            encounter.setVoided((Boolean) request.get(CommonFieldNames.VOIDED));

        encounter.validate();
        Set<String> observationKeys = request.containsKey(OBSERVATIONS) ? ((Map<String, Object>) request.get(OBSERVATIONS)).keySet() : new HashSet<>();
        mediaObservationService.patchMediaObservations(encounter.getObservations(), observationKeys);

        Set<String> cancelObservationKeys = request.containsKey(CANCEL_OBSERVATIONS) ? ((Map<String, Object>) request.get(CANCEL_OBSERVATIONS)).keySet() : new HashSet<>();
        mediaObservationService.patchMediaObservations(encounter.getCancelObservations(), cancelObservationKeys);

        return save(encounter);
    }
}
