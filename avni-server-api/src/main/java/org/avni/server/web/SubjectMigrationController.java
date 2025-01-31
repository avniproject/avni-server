package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.model.CustomJobParameter;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

import static java.lang.String.format;
import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class SubjectMigrationController extends AbstractController<SubjectMigration> implements RestControllerResourceProcessor<SubjectMigration>{
    private final SubjectMigrationRepository subjectMigrationRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private final Logger logger;
    private final ScopeBasedSyncService<SubjectMigration> scopeBasedSyncService;
    private final SubjectMigrationService subjectMigrationService;
    private final IndividualRepository individualRepository;
    private final LocationRepository locationRepository;
    private final AccessControlService accessControlService;
    private final Job bulkSubjectMigrationJob;
    private final JobLauncher bulkSubjectMigrationJobLauncher;

    @Autowired
    public SubjectMigrationController(SubjectMigrationRepository subjectMigrationRepository, SubjectTypeRepository subjectTypeRepository, UserService userService, ScopeBasedSyncService<SubjectMigration> scopeBasedSyncService, SubjectMigrationService subjectMigrationService, IndividualRepository individualRepository, LocationRepository locationRepository, AccessControlService accessControlService, Job bulkSubjectMigrationJob, JobLauncher bulkSubjectMigrationJobLauncher) {
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.subjectMigrationService = subjectMigrationService;
        this.individualRepository = individualRepository;
        this.locationRepository = locationRepository;
        this.accessControlService = accessControlService;
        this.bulkSubjectMigrationJob = bulkSubjectMigrationJob;
        this.bulkSubjectMigrationJobLauncher = bulkSubjectMigrationJobLauncher;
        logger = LoggerFactory.getLogger(this.getClass());
        this.subjectMigrationRepository = subjectMigrationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
    }

    @RequestMapping(value = "/subjectMigrations/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<SubjectMigration>> getMigrationsByCatchmentAndLastModifiedAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));

        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(subjectMigrationRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.SubjectMigration));
    }

    @RequestMapping(value = "/subjectMigrations", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<SubjectMigration>> getMigrationsByCatchmentAndLastModified(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));

        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(subjectMigrationRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.SubjectMigration));
    }

    @Override
    public EntityModel<SubjectMigration> process(EntityModel<SubjectMigration> resource) {
        SubjectMigration content = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(content.getIndividual().getUuid(), "individualUUID"));
        resource.add(Link.of(content.getSubjectType().getUuid(), "subjectTypeUUID"));
        if (content.getOldAddressLevel() != null)
            resource.add(Link.of(content.getOldAddressLevel().getUuid(), "oldAddressLevelUUID"));
        if (content.getNewAddressLevel() != null)
            resource.add(Link.of(content.getNewAddressLevel().getUuid(), "newAddressLevelUUID"));
        addAuditFields(content, resource);
        return resource;
    }

    @RequestMapping(value = "/api/subjectMigration/bulk", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity migrate(@RequestParam(value = "mode", defaultValue = "byAddress") SubjectMigrationService.BulkSubjectMigrationModes mode,
                                  @RequestBody BulkSubjectMigrationRequest bulkSubjectMigrationRequest) {
        accessControlService.checkPrivilege(PrivilegeType.MultiTxEntityTypeUpdate);
        if (bulkSubjectMigrationRequest.getSubjectIds() == null) {
            throw new BadRequestError("subjectIds is required");
        }
        if (mode == SubjectMigrationService.BulkSubjectMigrationModes.byAddress
                && (bulkSubjectMigrationRequest.getDestinationAddresses() == null || bulkSubjectMigrationRequest.getDestinationAddresses().isEmpty())) {
            throw new BadRequestError("destinationAddresses is required for mode: byAddress");
        }
        if (mode == SubjectMigrationService.BulkSubjectMigrationModes.bySyncConcept
                && (bulkSubjectMigrationRequest.getDestinationSyncConcepts() == null || bulkSubjectMigrationRequest.getDestinationSyncConcepts().isEmpty())) {
            throw new BadRequestError("destinationSyncConcepts is required for mode: bySyncConcept");
        }

        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Organisation organisation = userContext.getOrganisation();
        String jobUUID = UUID.randomUUID().toString();
        String fileName = format("%s-%s-%s.%s", jobUUID, mode, user.getUsername(), "json");
        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString("uuid", jobUUID)
                        .addString("organisationUUID", organisation.getUuid())
                        .addLong("userId", user.getId(), false)
                        .addString("mode", String.valueOf(mode))
                        .addString("fileName", fileName)
                        .addJobParameter("bulkSubjectMigrationParameters", new CustomJobParameter<>(bulkSubjectMigrationRequest))
                        .toJobParameters();

        try {
            bulkSubjectMigrationJobLauncher.run(bulkSubjectMigrationJob, jobParameters);
        } catch (JobParametersInvalidException | JobExecutionAlreadyRunningException |
                 JobInstanceAlreadyCompleteException | JobRestartException e) {
            throw new RuntimeException(String.format("Error while starting the bulk subject migration job, %s", e.getMessage()), e);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(subjectMigrationService.getBulkSubjectMigrationJobStatus(jobUUID));
    }

    @RequestMapping(value = "/api/subjectMigration/bulk/status/{jobUuid}", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity migrationStatus(@PathVariable("jobUuid") String jobUuid) {
        accessControlService.checkPrivilege(PrivilegeType.MultiTxEntityTypeUpdate);
        JobStatus jobStatus = subjectMigrationService.getBulkSubjectMigrationJobStatus(jobUuid);
        return jobStatus != null ? ResponseEntity.ok(jobStatus) : ResponseEntity.notFound().build();
    }
}
