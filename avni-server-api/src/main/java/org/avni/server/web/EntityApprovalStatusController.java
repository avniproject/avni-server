package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.EntityApprovalStatusRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.service.EntityApprovalStatusService;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.EntityApprovalStatusRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class EntityApprovalStatusController implements RestControllerResourceProcessor<EntityApprovalStatus> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private final ScopeBasedSyncService<EntityApprovalStatus> scopeBasedSyncService;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ProgramRepository programRepository;
    private final FormMappingService formMappingService;


    @Autowired
    public EntityApprovalStatusController(EntityApprovalStatusService entityApprovalStatusService,
                                          EntityApprovalStatusRepository entityApprovalStatusRepository,
                                          SubjectTypeRepository subjectTypeRepository, UserService userService,
                                          ScopeBasedSyncService<EntityApprovalStatus> scopeBasedSyncService,
                                          EncounterTypeRepository encounterTypeRepository,
                                          ProgramRepository programRepository,
                                          FormMappingService formMappingService) {
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.encounterTypeRepository = encounterTypeRepository;
        this.programRepository = programRepository;
        this.formMappingService = formMappingService;
    }

    @RequestMapping(value = "/entityApprovalStatuses", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody EntityApprovalStatusRequest request) {
        entityApprovalStatusService.save(request);
    }

    @RequestMapping(value = "/entityApprovalStatus", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<EntityApprovalStatus>> getEntityApprovals(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "entityType", required = false) SyncEntityName entityName,
            @RequestParam(value = "entityTypeUuid", required = false) String entityTypeUuid,
            Pageable pageable) {
        if (entityName == null) {
            return wrap(entityApprovalStatusRepository
                    .findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
                            CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
        }
        return getScopeBasedSyncResults(lastModifiedDateTime, now,
                fetchSubjectTypeForEntityNameAndUuid(entityName, entityTypeUuid), pageable, entityName, entityTypeUuid);
    }

    @RequestMapping(value = "/entityApprovalStatus/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<EntityApprovalStatus>> getEntityApprovalsAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "entityType", required = false) SyncEntityName entityName,
            @RequestParam(value = "entityTypeUuid", required = false) String entityTypeUuid,
            Pageable pageable) {

        return getScopeBasedSyncResultsAsSlice(lastModifiedDateTime, now,
                fetchSubjectTypeForEntityNameAndUuid(entityName, entityTypeUuid), pageable, entityName, entityTypeUuid);
    }

    @Override
    public EntityModel<EntityApprovalStatus> process(EntityModel<EntityApprovalStatus> resource) {
        EntityApprovalStatus entityApprovalStatus = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(entityApprovalStatusService.getEntityUuid(entityApprovalStatus), "entityUUID"));
        resource.add(Link.of(entityApprovalStatus.getApprovalStatus().getUuid(), "approvalStatusUUID"));
        addAuditFields(entityApprovalStatus, resource);
        return resource;
    }

    private String fetchSubjectTypeForEntityNameAndUuid(SyncEntityName entityName, String entityTypeUuid) {
        switch (entityName) {
            case Subject:
                return entityTypeUuid.isEmpty() ? null : entityTypeUuid;
            case Encounter:
                return getSubjectTypeUuidFromEncounterTypeUuid(entityTypeUuid, FormType.Encounter);
            case ProgramEncounter:
                return getSubjectTypeUuidFromEncounterTypeUuid(entityTypeUuid, FormType.ProgramEncounter);
            case ProgramEnrolment:
            case ChecklistItem:
                return getSubjectTypeUuidFromProgramTypeUuid(entityTypeUuid, FormType.ProgramEnrolment);
            default:
                return null;
        }
    }

    private String getSubjectTypeUuidFromEncounterTypeUuid(String entityTypeUuid, FormType formType) {
        if (entityTypeUuid.isEmpty()) return null;
        EncounterType encounterType = encounterTypeRepository.findByUuid(entityTypeUuid);
        if (encounterType == null) return null;
        FormMapping formMapping = formMappingService.find(encounterType, formType);
        if (formMapping == null)
            throw new RuntimeException(String.format("No form mapping found for encounter %s", encounterType.getName()));
        return formMapping.getSubjectTypeUuid();
    }

    private String getSubjectTypeUuidFromProgramTypeUuid(String entityTypeUuid, FormType formType) {
        if (entityTypeUuid.isEmpty()) return null;
        Program program = programRepository.findByUuid(entityTypeUuid);
        if (program == null) return null;
        FormMapping formMapping = formMappingService.find(program, formType);
        if (formMapping == null)
            throw new RuntimeException(String.format("No form mapping found for program %s", program.getName()));
        return formMapping.getSubjectTypeUuid();
    }

    private SlicedResources<EntityModel<EntityApprovalStatus>> getScopeBasedSyncResultsAsSlice(DateTime lastModifiedDateTime,
                                                                                            DateTime now, String subjectTypeUuid, Pageable pageable,
                                                                                            SyncEntityName entityName, String entityTypeUuid) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(entityApprovalStatusRepository,
                userService.getCurrentUser(), lastModifiedDateTime, now, entityTypeUuid, pageable, subjectType, entityName));
    }

    private CollectionModel<EntityModel<EntityApprovalStatus>> getScopeBasedSyncResults(DateTime lastModifiedDateTime,
                                                                                    DateTime now, String subjectTypeUuid, Pageable pageable,
                                                                                    SyncEntityName entityName, String entityTypeUuid) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(entityApprovalStatusRepository,
                userService.getCurrentUser(), lastModifiedDateTime, now, entityTypeUuid, pageable, subjectType, entityName));
    }
}
