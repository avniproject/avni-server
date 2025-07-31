package org.avni.server.web;


import jakarta.transaction.Transactional;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Group;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.web.request.UserGroupContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class UserGroupController extends AbstractController<UserGroup> implements RestControllerResourceProcessor<UserGroup> {
    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;
    private final MetabaseService metabaseService;

    @Autowired
    public UserGroupController(UserGroupRepository userGroupRepository, UserRepository userRepository, GroupRepository groupRepository,
                               AccessControlService accessControlService,
                               OrganisationConfigService organisationConfigService, MetabaseService metabaseService) {
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.metabaseService = metabaseService;
    }

    @RequestMapping(value = "/myGroups/search/lastModified", method = RequestMethod.GET)
    public CollectionModel<EntityModel<UserGroup>> get(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                   @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                   Pageable pageable) {
        User user = UserContextHolder.getUserContext().getUser();
        return wrap(userGroupRepository.findByUserIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(user.getId(), CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/groups/{id}/users", method = RequestMethod.GET)
    public List<UserGroupContract> getById(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        return userGroupRepository.findByGroup_IdAndIsVoidedFalse(id).stream()
                .map(UserGroupContract::fromEntity)
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/userGroups", method = RequestMethod.GET)
    public List<UserGroupContract> getByOrganisationId() {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        return userGroupRepository.findByOrganisationId(UserContextHolder.getUserContext().getOrganisationId()).stream()
                .map(UserGroupContract::fromEntity)
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/userGroup", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity addUsersToGroup(@RequestBody List<UserGroupContract> request) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        List<UserGroup> usersToBeAdded = new ArrayList<>();

        for (UserGroupContract userGroupContract : request) {
            User user = userRepository.findOne(userGroupContract.getUserId());
            Group group = groupRepository.findOne(userGroupContract.getGroupId());
            if (user == null || group == null) {
                return ResponseEntity.badRequest().body(String.format("Invalid user id %d or group id %d", userGroupContract.getUserId(), userGroupContract.getGroupId()));
            }
            UserGroup userGroup = userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group);
            if (userGroup == null) {
                userGroup = UserGroup.createMembership(user, group);
            }
            usersToBeAdded.add(userGroup);
        }
        if (organisationConfigService.isMetabaseSetupEnabled(UserContextHolder.getOrganisation())) {
            metabaseService.upsertUsersOnMetabase(usersToBeAdded);
        }
        return ResponseEntity.ok(userGroupRepository.saveAll(usersToBeAdded));
    }

    @RequestMapping(value = "/userGroup/{id}", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity removeUserFromGroup(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        UserGroup userGroup = userGroupRepository.findOne(id);
        if (userGroup == null)
            return ResponseEntity.badRequest().body(String.format("UserGroup with id '%d' not found", id));

        userGroup.setVoided(true);
        userGroupRepository.save(userGroup);
        if (organisationConfigService.isMetabaseSetupEnabled(UserContextHolder.getOrganisation()) &&
                userGroup.getGroupName().contains(Group.METABASE_USERS)) {
            metabaseService.upsertUsersOnMetabase(List.of(userGroup));
        }
        return new ResponseEntity<>(UserGroupContract.fromEntity(userGroup), HttpStatus.OK);
    }
}
