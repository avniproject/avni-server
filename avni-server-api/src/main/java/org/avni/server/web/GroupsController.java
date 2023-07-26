package org.avni.server.web;

import org.avni.server.dao.GroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.GroupsService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.GroupContract;
import org.avni.server.web.request.webapp.ProgramContractWeb;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;

@RestController
public class GroupsController implements RestControllerResourceProcessor<ProgramContractWeb> {
    private final GroupRepository groupRepository;
    private final GroupsService groupsService;
    private final AccessControlService accessControlService;

    public GroupsController(GroupRepository groupRepository, GroupsService groupsService, AccessControlService accessControlService) {
        this.groupRepository = groupRepository;
        this.groupsService = groupsService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/groups/search/findAllById")
    @ResponseBody
    public List<Group> findAllById(@Param("ids") Long[] ids) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        return groupRepository.findByIdIn(ids);
    }

    @GetMapping(value = "/web/groups")
    @ResponseBody
    public List<Group> getAll() {
        // since this is security related information added check for get also
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        return groupRepository.findAllByIsVoidedFalse();
    }

    @PostMapping(value = "web/groups")
    @Transactional
    public ResponseEntity saveGroup(@RequestBody GroupContract group) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        if (group.getName() == null || group.getName().trim().equals("")) {
            return ResponseEntity.badRequest().body("Group name cannot be blank.");
        }
        if (groupRepository.findByNameAndOrganisationId(group.getName(), organisation.getId()) != null) {
            return ResponseEntity.badRequest().body(String.format("Group with name %s already exists.", group.getName()));
        }
        Group savedGroup = groupsService.saveGroup(group);
        return ResponseEntity.ok(savedGroup);
    }

    @PutMapping(value = "web/group")
    @Transactional
    public ResponseEntity updateGroup(@RequestBody Group updatedGroup) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);

        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        Group group = groupRepository.findByIdAndOrganisationId(updatedGroup.getId(), organisation.getId());
        if (group == null) {
            return ResponseEntity.badRequest().body(String.format("Group with id %s not found.", updatedGroup.getId()));
        }

        if (group.isAdministrator() && !updatedGroup.isHasAllPrivileges()) {
            return ResponseEntity.badRequest().body("Admin group's all privileges flag cannot be changed");
        }

        if (!updatedGroup.getName().equals(group.getName()) && !group.isOneOfTheDefaultGroups()) {
            group.setName(updatedGroup.getName());
        }

        if (updatedGroup.isHasAllPrivileges() != group.isHasAllPrivileges()) {
            group.setHasAllPrivileges(updatedGroup.isHasAllPrivileges());
        }

        return ResponseEntity.ok(groupRepository.save(group));
    }

    @DeleteMapping(value = "/groups/{id}")
    @Transactional
    public ResponseEntity voidGroup(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        Group group = groupRepository.findOne(id);
        if (group == null)
            return ResponseEntity.badRequest().body(String.format("Group with id '%d' not found", id));
        if (group.isOneOfTheDefaultGroups())
            return ResponseEntity.badRequest().body(String.format("Default group %s cannot be deleted", group.getName()));

        group.setVoided(true);
        group.updateAudit();
        groupRepository.save(group);
        return ResponseEntity.ok(null);
    }
}
