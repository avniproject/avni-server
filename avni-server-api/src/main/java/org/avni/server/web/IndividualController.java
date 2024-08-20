package org.avni.server.web;

import com.bugsnag.Bugsnag;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.accessControl.SubjectPartitionData;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.geo.Point;
import org.avni.server.projection.IndividualWebProjection;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.EncounterContract;
import org.avni.server.web.request.IndividualRequest;
import org.avni.server.web.request.PointRequest;
import org.avni.server.web.request.SubjectSearchContract;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.avni.server.web.request.rules.RulesContractWrapper.IndividualContract;
import org.avni.server.web.request.rules.constructWrappers.IndividualConstructionService;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;
import static org.springframework.data.jpa.domain.Specifications.where;

@RestController
public class IndividualController extends AbstractController<Individual> implements RestControllerResourceProcessor<Individual> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final IndividualRepository individualRepository;
    private final LocationRepository locationRepository;
    private final GenderRepository genderRepository;
    private final ObservationService observationService;
    private final UserService userService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProjectionFactory projectionFactory;
    private final IndividualService individualService;
    private final EncounterService encounterService;
    private final IndividualSearchService individualSearchService;
    private final IdentifierAssignmentRepository identifierAssignmentRepository;

    private final IndividualConstructionService individualConstructionService;
    private final ScopeBasedSyncService<Individual> scopeBasedSyncService;
    private final SubjectMigrationService subjectMigrationService;
    private final AccessControlService accessControlService;
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final FormMappingService formMappingService;
    private final Bugsnag bugsnag;
    private final TxDataControllerHelper txDataControllerHelper;

    @Autowired
    public IndividualController(IndividualRepository individualRepository,
                                LocationRepository locationRepository,
                                GenderRepository genderRepository,
                                ObservationService observationService,
                                UserService userService,
                                SubjectTypeRepository subjectTypeRepository,
                                ProjectionFactory projectionFactory,
                                IndividualService individualService,
                                EncounterService encounterService,
                                IndividualSearchService individualSearchService,
                                IdentifierAssignmentRepository identifierAssignmentRepository,
                                IndividualConstructionService individualConstructionService,
                                ScopeBasedSyncService<Individual> scopeBasedSyncService, SubjectMigrationService subjectMigrationService, AccessControlService accessControlService, EntityApprovalStatusService entityApprovalStatusService, FormMappingService formMappingService, Bugsnag bugsnag, TxDataControllerHelper txDataControllerHelper) {
        this.individualRepository = individualRepository;
        this.locationRepository = locationRepository;
        this.genderRepository = genderRepository;
        this.observationService = observationService;
        this.userService = userService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.projectionFactory = projectionFactory;
        this.individualService = individualService;
        this.encounterService = encounterService;
        this.individualSearchService = individualSearchService;
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.individualConstructionService = individualConstructionService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.subjectMigrationService = subjectMigrationService;
        this.accessControlService = accessControlService;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.formMappingService = formMappingService;
        this.bugsnag = bugsnag;
        this.txDataControllerHelper = txDataControllerHelper;
    }

    // used in offline mode hence no access check
    @RequestMapping(value = "/individuals", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public AvniEntityResponse save(@RequestBody IndividualRequest individualRequest) {
        logger.info(String.format("Saving individual with UUID %s", individualRequest.getUuid()));

        Individual individual = createIndividual(individualRequest);

        logger.info(String.format("Saved individual with UUID %s", individualRequest.getUuid()));

        return new AvniEntityResponse(individual);
    }

    private void markSubjectMigrationIfRequired(IndividualRequest individualRequest, ObservationCollection newObservations) {
        subjectMigrationService.markSubjectMigrationIfRequired(individualRequest.getUuid(), null, getAddressLevel(individualRequest), null, newObservations, false);
    }

    private void addObservationsFromDecisions(ObservationCollection observations, Decisions decisions) {
        if (decisions != null && !decisions.getRegistrationDecisions().isEmpty()) {
            observations.putAll(observationService
                    .createObservationsFromDecisions(decisions.getRegistrationDecisions()));
        }
    }

    private void saveVisitSchedules(IndividualRequest individualRequest) {
        if (individualRequest.getVisitSchedules() != null && individualRequest.getVisitSchedules().size() > 0) {
            encounterService.saveVisitSchedules(individualRequest.getUuid(), individualRequest.getVisitSchedules(), null);
        }
    }

    private void saveIdentifierAssignments(Individual individual, IndividualRequest individualRequest) {
        List<String> identifierAssignmentUuids = individualRequest.getIdentifierAssignmentUuids();
        if (identifierAssignmentUuids != null) {
            identifierAssignmentUuids.forEach(uuid -> {
                IdentifierAssignment identifierAssignment = identifierAssignmentRepository.findByUuid(uuid);
                identifierAssignment.setIndividual(individual);
                identifierAssignmentRepository.save(identifierAssignment);
            });
        }
    }

    @GetMapping(value = {"/individual/v2", "/individual/search/lastModified/v2"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<Resource<Individual>> getIndividualsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(individualRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.Individual));
    }

    @GetMapping(value = {"/individual", /*-->Both are Deprecated */ "/individual/search/byCatchmentAndLastModified", "/individual/search/lastModified"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public PagedResources<Resource<Individual>> getIndividualsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(individualRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.Individual));
    }

    @GetMapping(value = "/individual/search")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public Page<IndividualWebProjection> search(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "subjectTypeUUID", required = false) String subjectTypeUUID,
            Pageable pageable) {
        IndividualRepository repo = this.individualRepository;
        return repo.findAll(
                where(repo.getFilterSpecForName(name))
                        .and(repo.getFilterSpecForSubjectTypeId(subjectTypeUUID))
                        .and(repo.getFilterSpecForVoid(false))
                , pageable)
                .map(t -> projectionFactory.createProjection(IndividualWebProjection.class, t));
    }

    @PostMapping(value = "/web/searchAPI/v2")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<LinkedHashMap<String, Object>> searchSubjects(@RequestBody SubjectSearchRequest subjectSearchRequest) {
        return new ResponseEntity<>(individualSearchService.search(subjectSearchRequest), HttpStatus.OK);
    }

    @GetMapping(value = "/web/individual/{uuid}")
    @ResponseBody
    public IndividualWebProjection getOneForWeb(@PathVariable String uuid) {
        IndividualWebProjection projection = projectionFactory.createProjection(IndividualWebProjection.class, individualRepository.findByUuid(uuid));
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, projection.getSubjectType().getUuid());
        return projection;
    }

    @GetMapping(value = "/web/individual")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public List<IndividualWebProjection> getByUUIDs(@RequestParam(value = "uuids") List<String> uuids) {
        List<IndividualWebProjection> projections = individualRepository.findAllIndividualWebProjectionByUuidIn(uuids);
        List<String> subjectTypeUUIDs = projections.stream().map(individualWebProjection -> individualWebProjection.getSubjectType().getUuid()).distinct().collect(Collectors.toList());
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, subjectTypeUUIDs);
        return projections;
    }

    @GetMapping(value = "/web/subjectProfile")
    @ResponseBody
    public ResponseEntity<org.avni.server.web.request.IndividualContract> getSubjectProfile(@RequestParam("uuid") String uuid) {
        org.avni.server.web.request.IndividualContract individualContract = individualService.getSubjectInfo(uuid);
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, individualContract.getSubjectType().getUuid());
        return ResponseEntity.ok(individualContract);
    }

    @GetMapping(value = "/web/subject/{subjectUuid}/programs")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<org.avni.server.web.request.IndividualContract> getSubjectProgramEnrollment(@PathVariable("subjectUuid") String uuid) {
        org.avni.server.web.request.IndividualContract individualEnrolmentContract = individualService.getSubjectProgramEnrollment(uuid);
        if (individualEnrolmentContract == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(individualEnrolmentContract);
    }

    @GetMapping(value = "/web/subject/{uuid}/encounters")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<org.avni.server.web.request.IndividualContract> getSubjectEncounters(@PathVariable("uuid") String uuid) {
        org.avni.server.web.request.IndividualContract individualEncounterContract = individualService.getSubjectEncounters(uuid);
        if (individualEncounterContract == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(individualEncounterContract);
    }

    @GetMapping("/web/subject/{uuid}/completed")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public Page<EncounterContract> getAllCompletedEncounters(
            @PathVariable String uuid,
            @RequestParam(value = "encounterDateTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime encounterDateTime,
            @RequestParam(value = "earliestVisitDateTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime earliestVisitDateTime,
            @RequestParam(value = "encounterTypeUuids", required = false) String encounterTypeUuids,
            Pageable pageable) {
        return encounterService.getAllCompletedEncounters(uuid, encounterTypeUuids, encounterDateTime, earliestVisitDateTime, pageable);
    }

    @DeleteMapping("/web/subject/{uuid}")
    @ResponseBody
    @Transactional
    public AvniEntityResponse voidSubject(@PathVariable String uuid) {
        try {
        Individual individual = individualRepository.findByUuid(uuid);
            txDataControllerHelper.checkSubjectAccess(individual);
        if (individual == null) {
                return AvniEntityResponse.error("Subject not found");
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.VoidSubject, individual);
        individualService.voidSubject(individual);
            return new AvniEntityResponse(individual);
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException | BadRequestError e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return AvniEntityResponse.error(e.getMessage());
        }
    }

    @GetMapping("/subject/search")
    @ResponseBody
    public List<IndividualContract> getAllSubjects(@RequestParam String addressLevelUUID,
                                                   @RequestParam String subjectTypeName) {
        AddressLevel addressLevel = locationRepository.findByUuid(addressLevelUUID);
        SubjectType subjectType = subjectTypeRepository.findByName(subjectTypeName);
        accessControlService.checkSubjectPrivilege(PrivilegeType.VoidSubject, subjectType);
        List<Individual> individuals = individualRepository.findAllByAddressLevelAndSubjectTypeAndIsVoidedFalse(addressLevel, subjectType);
        return individuals.stream().map(individualConstructionService::getSubjectInfo).collect(Collectors.toList());
    }

    @GetMapping(value = {"/subjects", "/subjects/search/find"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public Page<?> searchByName(@RequestParam(value = "name", required = false) String name, Pageable pageable) {
        if (name == null || name.isEmpty()) {
            return new PageImpl<>(Collections.emptyList());
        }
        return this.individualRepository.findByName(name, pageable)
                .map(SubjectSearchContract::fromSubject);
    }

    @GetMapping(value = "/subjects/search/findAllById")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public Page<SubjectSearchContract> findByIds(@Param("ids") Long[] ids, Pageable pageable) {
        return this.individualRepository.findByIdIn(ids, pageable).map(SubjectSearchContract::fromSubject);
    }

    @GetMapping(value = "/web/individual/byMetadata")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<IndividualWebProjection> findByMetadata(
            @Param(value = "subjectTypeName") String subjectTypeName,
            @Param(value = "programName") String programName,
            @Param(value = "encounterTypeName") String encounterTypeName,
            @RequestParam(value = "entityId") String entityId) {
        try {
            Individual individual = this.individualService.findByMetadata(subjectTypeName, programName, encounterTypeName, Long.parseLong(entityId));
            if (individual == null) {
                return ResponseEntity.badRequest().build();
            }
            IndividualWebProjection individualWebProjection = this.projectionFactory.createProjection(IndividualWebProjection.class, individual);
            return ResponseEntity.ok(individualWebProjection);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public Resource<Individual> process(Resource<Individual> resource) {
        Individual individual = resource.getContent();
        resource.removeLinks();
        if (individual.getAddressLevel() != null) {
            resource.add(new Link(individual.getAddressLevel().getUuid(), "addressUUID"));
        }
        if (individual.getGender() != null) {
            resource.add(new Link(individual.getGender().getUuid(), "genderUUID"));
        }
        if (individual.getSubjectType() != null) {
            resource.add(new Link(individual.getSubjectType().getUuid(), "subjectTypeUUID"));
        }
        addAuditFields(individual, resource);
        return resource;
    }

    @RequestMapping(value = "/web/individuals", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public AvniEntityResponse saveForWeb(@RequestBody IndividualRequest individualRequest) {
        try {
        logger.info(String.format("Saving individual with UUID %s", individualRequest.getUuid()));
            Individual savedIndividual = individualRepository.findEntity(individualRequest.getUuid());
            //Subject is changed after this line, hence the following line cannot be moved down closer to its usage
            SubjectPartitionData subjectPartitionData = SubjectPartitionData.create(savedIndividual);

        Individual individual = createIndividual(individualRequest);

        FormMapping formMapping = formMappingService.findBy(individual.getSubjectType(), null, null, FormType.IndividualProfile);
        entityApprovalStatusService.createStatus(EntityApprovalStatus.EntityType.Subject, individual.getId(), ApprovalStatus.Status.Pending, individual.getSubjectType().getUuid(), formMapping);
            // Sync attribute values are picked from the field on individual and not from observations, hence this should be done after the individual is saved
            txDataControllerHelper.checkSubjectAccess(individual, subjectPartitionData);
        logger.info(String.format("Saved individual with UUID %s", individualRequest.getUuid()));

        return new AvniEntityResponse(individual);
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return AvniEntityResponse.error(e.getMessage());
        }
    }

    private Individual createIndividual(IndividualRequest individualRequest) {
        Decisions decisions = individualRequest.getDecisions();
        observationService.validateObservationsAndDecisions(individualRequest.getObservations(), decisions != null ? decisions.getRegistrationDecisions() : null, formMappingService.findForSubject(individualRequest.getSubjectTypeUUID()));
        ObservationCollection observations = observationService.createObservations(individualRequest.getObservations());
        addObservationsFromDecisions(observations, individualRequest.getDecisions());
        this.markSubjectMigrationIfRequired(individualRequest, observations);

        Individual individual = createIndividualWithoutObservations(individualRequest);

        // Temporary fix to not allow emptying of observations
        if ((individualRequest.getObservations() == null || individualRequest.getObservations().isEmpty()) && individual.getObservations() != null && !individual.getObservations().isEmpty()) {
            String errorMessage = String.format("Individual Observations not all allowed to be made empty. User: %s, UUID: %s, ", UserContextHolder.getUser().getUsername(), individualRequest.getUuid());
            bugsnag.notify(new Exception(errorMessage));
            logger.error(errorMessage);
            individual.updateAudit();
        } else {
        individual.setObservations(observations);
        }

        Individual savedIndividual = individualService.save(individual);
        saveVisitSchedules(individualRequest);
        saveIdentifierAssignments(savedIndividual, individualRequest);
        return savedIndividual;
    }

    private Individual createIndividualWithoutObservations(@RequestBody IndividualRequest individualRequest) {
        AddressLevel addressLevel = getAddressLevel(individualRequest);
        Gender gender = individualRequest.getGender() == null ? genderRepository.findByUuid(individualRequest.getGenderUUID()) : genderRepository.findByName(individualRequest.getGender());
        SubjectType subjectType = individualRequest.getSubjectTypeUUID() == null ? subjectTypeRepository.findByUuid("9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3") : subjectTypeRepository.findByUuid(individualRequest.getSubjectTypeUUID());
        Individual individual = newOrExistingEntity(individualRepository, individualRequest, new Individual());
        individual.setSubjectType(subjectType);
        individual.setFirstName(individualRequest.getFirstName());
        individual.setMiddleName(individualRequest.getMiddleName());
        individual.setLastName(individualRequest.getLastName());
        if (subjectType.isAllowProfilePicture()) {
            individual.setProfilePicture(individualRequest.getProfilePicture());
        }
        individual.setDateOfBirth(individualRequest.getDateOfBirth());
        individual.setAddressLevel(addressLevel);
        individual.setGender(gender);
        individual.setRegistrationDate(individualRequest.getRegistrationDate());
        individual.setVoided(individualRequest.isVoided());
        PointRequest pointRequest = individualRequest.getRegistrationLocation();
        if (pointRequest != null)
            individual.setRegistrationLocation(new Point(pointRequest.getX(), pointRequest.getY()));
        return individual;
    }

    private AddressLevel getAddressLevel(@RequestBody IndividualRequest individualRequest) {
        if (individualRequest.getAddressLevelUUID() != null) {
            return locationRepository.findByUuid(individualRequest.getAddressLevelUUID());
        } else if (individualRequest.getCatchmentUUID() != null) {
            return locationRepository.findByTitleAndCatchmentsUuid(individualRequest.getAddressLevel(), individualRequest.getCatchmentUUID());
        } else {
            return locationRepository.findByTitleIgnoreCase(individualRequest.getAddressLevel());
        }
    }

}
