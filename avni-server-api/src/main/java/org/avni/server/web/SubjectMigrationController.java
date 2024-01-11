package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.SubjectMigrationRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    public SubjectMigrationController(SubjectMigrationRepository subjectMigrationRepository, SubjectTypeRepository subjectTypeRepository, UserService userService, ScopeBasedSyncService<SubjectMigration> scopeBasedSyncService, SubjectMigrationService subjectMigrationService, IndividualRepository individualRepository, LocationRepository locationRepository, AccessControlService accessControlService, AddressLevelTypeRepository addressLevelTypeRepository) {
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.subjectMigrationService = subjectMigrationService;
        this.individualRepository = individualRepository;
        this.locationRepository = locationRepository;
        this.accessControlService = accessControlService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        logger = LoggerFactory.getLogger(this.getClass());
        this.subjectMigrationRepository = subjectMigrationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
    }

    @RequestMapping(value = "/subjectMigrations/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<Resource<SubjectMigration>> getMigrationsByCatchmentAndLastModifiedAsSlice(
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
    public PagedResources<Resource<SubjectMigration>> getMigrationsByCatchmentAndLastModified(
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
    public Resource<SubjectMigration> process(Resource<SubjectMigration> resource) {
        SubjectMigration content = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(content.getIndividual().getUuid(), "individualUUID"));
        resource.add(new Link(content.getSubjectType().getUuid(), "subjectTypeUUID"));
        if (content.getOldAddressLevel() != null)
            resource.add(new Link(content.getOldAddressLevel().getUuid(), "oldAddressLevelUUID"));
        if (content.getNewAddressLevel() != null)
            resource.add(new Link(content.getNewAddressLevel().getUuid(), "newAddressLevelUUID"));
        addAuditFields(content, resource);
        return resource;
    }

    @RequestMapping(value = "/subjectMigration/bulk", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void migrate(@RequestBody SubjectMigrationRequest subjectMigrationRequest) {
        Map<String, String> destinationAddresses = subjectMigrationRequest.getDestinationAddresses();

        for (Map.Entry<String, String> destinationAddressEntry : destinationAddresses.entrySet()) {
            Long source = Long.parseLong(destinationAddressEntry.getKey());
            Long dest = Long.parseLong(destinationAddressEntry.getValue());

            AddressLevel sourceAddressLevel = locationRepository.findOne(source);
            AddressLevel destAddressLevel = locationRepository.findOne(dest);

            subjectMigrationRequest.getSubjectTypeIds().forEach(subjectTypeId -> {
                SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
                accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, subjectType.getUuid());
                List<Individual> subjects = individualRepository.findAllByAddressLevelAndSubjectType(sourceAddressLevel, subjectType);
                logger.info(String.format("Migrating for subject type: %s, for source address: %s, to destination address: %s, containing %d subjects", subjectType.getName(), source, dest, subjects.size()));
                subjectMigrationService.changeSubjectsAddressLevel(subjects, destAddressLevel);
            });
        }
    }
}
