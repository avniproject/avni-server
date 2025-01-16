package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.IndividualRelationshipRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
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
import java.util.Optional;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class IndividualRelationshipController extends AbstractController<IndividualRelationship> implements RestControllerResourceProcessor<IndividualRelationship> {
    private final IndividualRepository individualRepository;
    private final IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    private final IndividualRelationshipRepository individualRelationshipRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private final ScopeBasedSyncService<IndividualRelationship> scopeBasedSyncService;
    private final AccessControlService accessControlService;

    @Autowired
    public IndividualRelationshipController(IndividualRelationshipRepository individualRelationshipRepository, IndividualRepository individualRepository, IndividualRelationshipTypeRepository individualRelationshipTypeRepository, SubjectTypeRepository subjectTypeRepository, UserService userService, ScopeBasedSyncService<IndividualRelationship> scopeBasedSyncService, AccessControlService accessControlService) {
        this.individualRelationshipRepository = individualRelationshipRepository;
        this.individualRepository = individualRepository;
        this.individualRelationshipTypeRepository = individualRelationshipTypeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/individualRelationships", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public void save(@RequestBody IndividualRelationshipRequest request) {
        IndividualRelationshipType relationshipType = individualRelationshipTypeRepository.findByUuid(request.getRelationshipTypeUUID());
        Individual individualA = individualRepository.findByUuid(request.getIndividualAUUID());
        Individual individualB = individualRepository.findByUuid(request.getIndividualBUUID());

        IndividualRelationship individualRelationship = newOrExistingEntity(individualRelationshipRepository, request, new IndividualRelationship());
        individualRelationship.setIndividuala(individualA);
        individualRelationship.setRelationship(relationshipType);
        individualRelationship.setIndividualB(individualB);
        individualRelationship.setEnterDateTime(request.getEnterDateTime());
        individualRelationship.setExitDateTime(request.getExitDateTime());
        individualRelationship.setVoided(request.isVoided());

        individualRelationshipRepository.save(individualRelationship);
    }

    @RequestMapping(value = "/individualRelationship/search/byIndividualsOfCatchmentAndLastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<IndividualRelationship>> getByIndividualsOfCatchmentAndLastModified(
            @RequestParam("catchmentId") long catchmentId,
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(individualRelationshipRepository.findByIndividualaAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(catchmentId, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/individualRelationship/search/lastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<IndividualRelationship>> getByLastModified(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(individualRelationshipRepository.findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime, now, pageable));
    }

    @RequestMapping(value = "/individualRelationship/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<IndividualRelationship>> getIndividualRelationshipsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(individualRelationshipRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.IndividualRelationship));
    }

    @RequestMapping(value = "/individualRelationship", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<IndividualRelationship>> getIndividualRelationshipsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(individualRelationshipRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.IndividualRelationship));
    }

    @Override
    public EntityModel<IndividualRelationship> process(EntityModel<IndividualRelationship> resource) {
        IndividualRelationship individualRelationship = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(individualRelationship.getRelationship().getUuid(), "relationshipTypeUUID"));
        resource.add(Link.of(individualRelationship.getIndividuala().getUuid(), "individualAUUID"));
        resource.add(Link.of(individualRelationship.getIndividualB().getUuid(), "individualBUUID"));
        addAuditFields(individualRelationship, resource);
        return resource;
    }


    @DeleteMapping(value = "/web/relationShip/{id}")
    @ResponseBody
    @Transactional
    public void deleteIndividualRelationShip(@PathVariable Long id) {
        Optional<IndividualRelationship> relationShip = individualRelationshipRepository.findById(id);
        if (relationShip.isPresent()) {
            IndividualRelationship individualRelationShip = relationShip.get();
            accessControlService.checkSubjectPrivileges(PrivilegeType.VoidSubject, individualRelationShip.getIndividuala(), individualRelationShip.getIndividualB());
            individualRelationShip.setVoided(true);
            individualRelationshipRepository.save(individualRelationShip);
        }
    }
}
