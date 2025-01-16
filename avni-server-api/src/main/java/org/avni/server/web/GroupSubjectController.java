package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.GroupSubjectService;
import org.avni.server.service.IndividualService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.GroupRoleContract;
import org.avni.server.web.request.GroupSubjectContract;
import org.avni.server.web.request.GroupSubjectContractWeb;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.Logger;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class GroupSubjectController extends AbstractController<GroupSubject> implements RestControllerResourceProcessor<GroupSubject> {

    private final GroupSubjectRepository groupSubjectRepository;
    private final UserService userService;
    private final IndividualRepository individualRepository;
    private final GroupRoleRepository groupRoleRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualService individualService;
    private final Logger logger;
    private final ScopeBasedSyncService<GroupSubject> scopeBasedSyncService;
    private final GroupSubjectService groupSubjectService;
    private final AccessControlService accessControlService;

    @Autowired
    public GroupSubjectController(GroupSubjectRepository groupSubjectRepository, UserService userService, IndividualRepository individualRepository, GroupRoleRepository groupRoleRepository, SubjectTypeRepository subjectTypeRepository, IndividualService individualService, ScopeBasedSyncService<GroupSubject> scopeBasedSyncService, GroupSubjectService groupSubjectService, AccessControlService accessControlService) {
        this.groupSubjectRepository = groupSubjectRepository;
        this.userService = userService;
        this.individualRepository = individualRepository;
        this.groupRoleRepository = groupRoleRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualService = individualService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.groupSubjectService = groupSubjectService;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/groupSubject/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<GroupSubject>> getGroupSubjectsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String groupSubjectTypeUuid,
            Pageable pageable) {
        if (groupSubjectTypeUuid == null || groupSubjectTypeUuid.isEmpty())
            return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(groupSubjectTypeUuid);
        if(subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(groupSubjectRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.GroupSubject));
    }

    @RequestMapping(value = "/groupSubject", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<GroupSubject>> getGroupSubjectsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String groupSubjectTypeUuid,
            Pageable pageable) {
        if (groupSubjectTypeUuid == null || groupSubjectTypeUuid.isEmpty())
            return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(groupSubjectTypeUuid);
        if(subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(groupSubjectRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.GroupSubject));
    }

    @RequestMapping(value = "/groupSubjects", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody GroupSubjectContract request) throws ValidationException {
        Individual groupSubject = individualRepository.findByUuid(request.getGroupSubjectUUID());
        Individual memberSubject = individualRepository.findByUuid(request.getMemberSubjectUUID());
        GroupRole groupRole = groupRoleRepository.findByUuid(request.getGroupRoleUUID());

        GroupSubject existingOrNewGroupSubject = newOrExistingEntity(groupSubjectRepository, request, new GroupSubject());
        existingOrNewGroupSubject.setGroupSubject(groupSubject);
        existingOrNewGroupSubject.setMemberSubject(memberSubject);
        existingOrNewGroupSubject.setGroupRole(groupRole);
        existingOrNewGroupSubject.setMembershipStartDate(request.getMembershipStartDate() != null ? request.getMembershipStartDate() : new DateTime());
        existingOrNewGroupSubject.setMembershipEndDate(request.getMembershipEndDate());
        existingOrNewGroupSubject.setVoided(request.isVoided());
        groupSubjectService.save(existingOrNewGroupSubject);
    }

    @RequestMapping(value = "/groupSubjects/{groupSubjectUuid}", method = RequestMethod.DELETE)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void delete(@PathVariable String groupSubjectUuid) {
        GroupSubject groupSubject = groupSubjectRepository.findByUuid(groupSubjectUuid);
        if (groupSubject != null) {
            accessControlService.checkSubjectPrivilege(PrivilegeType.VoidSubject, groupSubject);
            groupSubject.setVoided(true);
            groupSubjectRepository.save(groupSubject);
        } else {
            throw new BadRequestError("Invalid GroupSubject Uuid");
        }
    }

    @RequestMapping(value = "/web/groupSubjects/{groupUuid}/members", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public List<GroupSubjectContractWeb> getGroupMembers(@PathVariable String groupUuid) {
        Individual group = individualRepository.findByUuid(groupUuid);
        if (group != null) {
            accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, group);
            List<GroupSubject> groupSubjects = groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group);
            return groupSubjects.stream().map(groupSubject -> {
                Individual member = individualRepository.findByUuid(groupSubject.getMemberSubjectUUID());
                GroupRole groupRole = groupRoleRepository.findByUuid(groupSubject.getGroupRole().getUuid());
                return individualService.createGroupSubjectContractWeb(groupSubject.getUuid(), member, groupRole);
            }).collect(Collectors.toList());
        } else {
            throw new BadRequestError("Invalid Group Id");
        }
    }

    @RequestMapping(value = "/web/groupSubjects/{groupUuid}/roles", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public List<GroupRoleContract> getGroupRoles(@PathVariable String groupUuid) {
        Individual group = individualRepository.findByUuid(groupUuid);
        if (group != null) {
            accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, group);
            return groupRoleRepository.findByGroupSubjectType_IdAndIsVoidedFalse(group.getSubjectType().getId())
                    .stream()
                    .map(GroupRoleContract::fromEntity).collect(Collectors.toList());
        } else {
            throw new BadRequestError("Invalid Group Id");
        }
    }

    @Override
    public EntityModel<GroupSubject> process(EntityModel<GroupSubject> resource) {
        GroupSubject groupSubject = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(groupSubject.getGroupSubject().getUuid(), "groupSubjectUUID"));
        resource.add(Link.of(groupSubject.getMemberSubject().getUuid(), "memberSubjectUUID"));
        resource.add(Link.of(groupSubject.getGroupRole().getUuid(), "groupRoleUUID"));
        addAuditFields(groupSubject, resource);
        return resource;
    }

}
