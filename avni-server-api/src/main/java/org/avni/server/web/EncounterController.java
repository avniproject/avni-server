package org.avni.server.web;

import com.bugsnag.Bugsnag;
import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.geo.Point;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.EncounterContract;
import org.avni.server.web.request.EncounterRequest;
import org.avni.server.web.request.PointRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;
import static org.avni.server.web.resourceProcessors.ResourceProcessor.addUserFields;

@RestController
public class EncounterController extends AbstractController<Encounter> implements RestControllerResourceProcessor<Encounter> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final IndividualRepository individualRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final EncounterRepository encounterRepository;
    private final ObservationService observationService;
    private final UserService userService;
    private final Bugsnag bugsnag;
    private final EncounterService encounterService;
    private final ScopeBasedSyncService<Encounter> scopeBasedSyncService;
    private final FormMappingService formMappingService;
    private final AccessControlService accessControlService;
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final TxDataControllerHelper txDataControllerHelper;

    @Autowired
    public EncounterController(IndividualRepository individualRepository,
                               EncounterTypeRepository encounterTypeRepository,
                               EncounterRepository encounterRepository,
                               ObservationService observationService,
                               UserService userService,
                               Bugsnag bugsnag,
                               EncounterService encounterService, ScopeBasedSyncService<Encounter> scopeBasedSyncService, FormMappingService formMappingService, AccessControlService accessControlService, EntityApprovalStatusService entityApprovalStatusService, TxDataControllerHelper txDataControllerHelper) {
        this.individualRepository = individualRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.encounterRepository = encounterRepository;
        this.observationService = observationService;
        this.userService = userService;
        this.bugsnag = bugsnag;
        this.encounterService = encounterService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.formMappingService = formMappingService;
        this.accessControlService = accessControlService;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.txDataControllerHelper = txDataControllerHelper;
    }

    @GetMapping(value = "/web/encounter/{uuid}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EncounterContract> getEncounterByUuid(@PathVariable("uuid") String uuid) {
        EncounterContract encounterContract = encounterService.getEncounterByUuid(uuid);
        if (encounterContract == null)
            return ResponseEntity.notFound().build();
        accessControlService.checkEncounterPrivilege(PrivilegeType.ViewVisit, encounterContract.getEncounterType().getUuid());
        return ResponseEntity.ok(encounterContract);
    }

    private void checkForSchedulingCompleteConstraintViolation(EncounterRequest request) {
        if ((request.getEarliestVisitDateTime() != null || request.getMaxVisitDateTime() != null)
            && (request.getEarliestVisitDateTime() == null || request.getMaxVisitDateTime() == null)
        ) {
            //violating constraint so notify bugsnag
            bugsnag.notify(new Exception(String.format("ProgramEncounter violating scheduling constraint uuid %s earliest %s max %s", request.getUuid(), request.getEarliestVisitDateTime(), request.getMaxVisitDateTime())));
        }
    }

    @RequestMapping(value = "/encounters", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody EncounterRequest request) throws ValidationException {
        logger.info(String.format("Saving encounter with uuid %s", request.getUuid()));

        createEncounter(request);

        logger.info(String.format("Saved encounter with uuid %s", request.getUuid()));
    }

    @RequestMapping(value = "/web/encounters", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public AvniEntityResponse saveForWeb(@RequestBody EncounterRequest request) throws ValidationException {
        try {
            logger.info("Saving encounter with uuid {}}", request.getUuid());
            Encounter encounter = createEncounter(request);
            if (encounter != null) // create encounter method needs fixing. it should not return null in any case
                txDataControllerHelper.checkSubjectAccess(encounter.getIndividual());
            addEntityApprovalStatusIfRequired(encounter);
            logger.info(String.format("Saved encounter with uuid %s", request.getUuid()));
            return new AvniEntityResponse(encounter);
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException e) {
            return AvniEntityResponse.error(e.getMessage());
        }
    }

    private void addEntityApprovalStatusIfRequired(Encounter encounter) {
        FormMapping formMapping = encounterService.getFormMapping(encounter);

        entityApprovalStatusService.createStatus(EntityApprovalStatus.EntityType.Encounter, encounter.getId(), ApprovalStatus.Status.Pending, encounter.getEncounterType().getUuid(), formMapping);
    }

    private Encounter createEncounter(EncounterRequest request) throws ValidationException {
        checkForSchedulingCompleteConstraintViolation(request);

        EncounterType encounterType = encounterTypeRepository.findByUuidOrName(request.getEncounterTypeUUID(), request.getEncounterType());
        Decisions decisions = request.getDecisions();
        observationService.validateObservationsAndDecisions(request.getObservations(), decisions != null ? decisions.getEncounterDecisions() : null, formMappingService.find(encounterType, FormType.Encounter));
        Individual individual = individualRepository.findByUuid(request.getIndividualUUID());
        if (individual == null) {
            throw new IllegalArgumentException(String.format("Individual not found with UUID '%s'", request.getIndividualUUID()));
        }

        Encounter encounter = newOrExistingEntity(encounterRepository, request, new Encounter());
        encounter.setIndividual(individual);
        //Planned visit can not overwrite completed encounter
        if (encounter.isCompleted() && request.isPlanned())
            return null;

        if ((request.getObservations() == null || request.getObservations().isEmpty()) && encounter.getObservations() != null && !encounter.getObservations().isEmpty()) {
            String errorMessage = String.format("Encounter Observations is getting empty. User: %s, UUID: %s, ", UserContextHolder.getUser().getUsername(), request.getUuid());
            bugsnag.notify(new Exception(errorMessage));
            logger.error(errorMessage);
        }
        if ((request.getCancelObservations() == null || request.getCancelObservations().isEmpty()) && encounter.getCancelObservations() != null && !encounter.getCancelObservations().isEmpty()) {
            String errorMessage = String.format("Encounter Cancel Observations is getting empty. User: %s, UUID: %s, ", UserContextHolder.getUser().getUsername(), request.getUuid());
            bugsnag.notify(new Exception(errorMessage));
            logger.error(errorMessage);
        }
        encounter.setEncounterDateTime(request.getEncounterDateTime(), userService.getCurrentUser());
        encounter.setEncounterType(encounterType);
        encounter.setObservations(observationService.createObservations(request.getObservations()));
        encounter.setName(request.getName());
        encounter.setEarliestVisitDateTime(request.getEarliestVisitDateTime());
        encounter.setMaxVisitDateTime(request.getMaxVisitDateTime());
        encounter.setCancelDateTime(request.getCancelDateTime());
        encounter.setCancelObservations(observationService.createObservations(request.getCancelObservations()));
        encounter.setVoided(request.isVoided());
        encounter.setCreatedBy(userService.getCurrentUser());
        encounter.setLastModifiedBy(userService.getCurrentUser());
        encounter.setFilledBy(userService.getCurrentUser());
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

            List<Decision> registrationDecisions = decisions.getRegistrationDecisions();
            if (registrationDecisions != null) {
                ObservationCollection registrationObservations = observationService.createObservationsFromDecisions(registrationDecisions);
                encounter.getIndividual().addObservations(registrationObservations);
            }
        }
        this.encounterService.save(encounter);

        if (request.getVisitSchedules() != null && !request.getVisitSchedules().isEmpty()) {
            this.encounterService.saveVisitSchedules(individual.getUuid(), request.getVisitSchedules(), request.getUuid());
        }
        return encounter;
    }

    @RequestMapping(value = "/encounter/search/byIndividualsOfCatchmentAndLastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Encounter>> getEncountersByCatchmentAndLastModified(
        @RequestParam("catchmentId") long catchmentId,
        @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
        Pageable pageable) {
        return wrap(encounterRepository.findByIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(catchmentId, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/encounter/search/lastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Encounter>> getEncountersByLastModified(
        @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
        Pageable pageable) {
        return wrap(encounterRepository.findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/encounter/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<Encounter>> getEncountersByOperatingIndividualScopeAsSlice(
        @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
        @RequestParam(value = "encounterTypeUuid", required = false) String encounterTypeUuid,
        Pageable pageable) throws Exception {
        if (encounterTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUuid);
        if (encounterType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        FormMapping formMapping = formMappingService.find(encounterType, FormType.Encounter);
        if (formMapping == null)
            throw new Exception(String.format("No form mapping found for encounter %s", encounterType.getName()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(encounterRepository, userService.getCurrentUser(), lastModifiedDateTime, now, encounterType.getId(), pageable, formMapping.getSubjectType(), SyncEntityName.Encounter));
    }

    @RequestMapping(value = "/encounter", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Encounter>> getEncountersByOperatingIndividualScope(
        @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
        @RequestParam(value = "encounterTypeUuid", required = false) String encounterTypeUuid,
        Pageable pageable) throws Exception {
        if (encounterTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUuid);
        if (encounterType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        FormMapping formMapping = formMappingService.find(encounterType, FormType.Encounter);
        if (formMapping == null)
            throw new Exception(String.format("No form mapping found for encounter %s", encounterType.getName()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(encounterRepository, userService.getCurrentUser(), lastModifiedDateTime, now, encounterType.getId(), pageable, formMapping.getSubjectType(), SyncEntityName.Encounter));
    }

    @DeleteMapping("/web/encounter/{uuid}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public AvniEntityResponse voidEncounter(@PathVariable String uuid) {
        try {
            Encounter encounter = encounterRepository.findByUuid(uuid);
            if (encounter == null) {
                return AvniEntityResponse.error("Encounter not found");
            }
            txDataControllerHelper.checkSubjectAccess(encounter.getIndividual());
            accessControlService.checkEncounterPrivilege(PrivilegeType.VoidVisit, encounter);
            encounter.setVoided(true);
            encounterService.save(encounter);
            return new AvniEntityResponse(encounter);
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return AvniEntityResponse.error(e.getMessage());
        }
    }

    @Override
    public EntityModel<Encounter> process(EntityModel<Encounter> resource) {
        Encounter encounter = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(encounter.getEncounterType().getUuid(), "encounterTypeUUID"));
        resource.add(Link.of(encounter.getIndividual().getUuid(), "individualUUID"));
        addAuditFields(encounter, resource);
        addUserFields(encounter.getFilledBy(), resource, "filledBy");
        addUserFields(encounter.getCreatedBy(), resource, "createdBy");
        addUserFields(encounter.getLastModifiedBy(), resource, "lastModifiedBy");
        return resource;
    }
}
