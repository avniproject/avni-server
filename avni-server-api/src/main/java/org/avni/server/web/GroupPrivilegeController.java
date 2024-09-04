package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.domain.Group;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.request.GroupPrivilegeContract;
import org.avni.server.web.request.GroupPrivilegeWebRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class GroupPrivilegeController extends AbstractController<GroupPrivilege> implements RestControllerResourceProcessor<GroupPrivilege> {
    private final GroupPrivilegeRepository groupPrivilegeRepository;
    private final GroupRepository groupRepository;
    private final PrivilegeRepository privilegeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final GroupPrivilegeService groupPrivilegeService;
    private final AccessControlService accessControlService;

    public GroupPrivilegeController(GroupPrivilegeRepository groupPrivilegeRepository, GroupRepository groupRepository, PrivilegeRepository privilegeRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, ChecklistDetailRepository checklistDetailRepository, GroupPrivilegeService groupPrivilegeService, AccessControlService accessControlService) {
        this.groupPrivilegeRepository = groupPrivilegeRepository;
        this.groupRepository = groupRepository;
        this.privilegeRepository = privilegeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.groupPrivilegeService = groupPrivilegeService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/groups/{id}/privileges", method = RequestMethod.GET)
    public List<GroupPrivilegeContract> getById(@PathVariable("id") Long id) {
        List<GroupPrivilege> allPossibleGroupPrivileges = groupPrivilegeService.getAllGroupPrivileges(id);
        List<GroupPrivilege> groupPrivileges = groupPrivilegeRepository.findByGroup_IdAndImplVersion(id, GroupPrivilege.IMPL_VERSION);
        groupPrivileges.addAll(allPossibleGroupPrivileges);
        return groupPrivileges.stream()
                .map(GroupPrivilegeContract::fromEntity)
                .distinct()
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/groupPrivilege", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity addOrUpdateGroupPrivileges(@RequestBody List<GroupPrivilegeWebRequest> request) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserGroup);
        List<GroupPrivilege> privilegesToBeAddedOrUpdated = new ArrayList<>();

        for (GroupPrivilegeWebRequest groupPrivilegeRequest : request) {
            //continue to rely on uuid here since privilege list on webapp will either be new records or valid impl_version existing record for the same org
            GroupPrivilege groupPrivilege = groupPrivilegeRepository.findByUuid(groupPrivilegeRequest.getUuid());
            if (groupPrivilege != null) {
                groupPrivilege.setAllow(groupPrivilegeRequest.isAllow());
                privilegesToBeAddedOrUpdated.add(groupPrivilege);
            } else {
                groupPrivilege = new GroupPrivilege();
                Optional<Privilege> optionalPrivilege = privilegeRepository.findById(groupPrivilegeRequest.getPrivilegeId());
                Group group = groupRepository.findOne(groupPrivilegeRequest.getGroupId());
                SubjectType subjectType = null;
                if (groupPrivilegeRequest.getSubjectTypeId() != null) {
                    subjectType = subjectTypeRepository.findOne(groupPrivilegeRequest.getSubjectTypeId());
                }

                if (!optionalPrivilege.isPresent() || group == null) {
                    return ResponseEntity.badRequest().body(String.format("Invalid privilege id %d or group id %d", groupPrivilegeRequest.getPrivilegeId(), groupPrivilegeRequest.getGroupId()));
                }
                groupPrivilege.setUuid(groupPrivilegeRequest.getUuid());
                groupPrivilege.setPrivilege(optionalPrivilege.get());
                groupPrivilege.setGroup(group);
                groupPrivilege.setSubjectType(subjectType);
                groupPrivilege.setProgram(groupPrivilegeRequest.getProgramId() != null ? programRepository.findOne(groupPrivilegeRequest.getProgramId()) : null);
                groupPrivilege.setEncounterType(groupPrivilegeRequest.getEncounterTypeId() != null ? encounterTypeRepository.findOne(groupPrivilegeRequest.getEncounterTypeId()) : null);
                groupPrivilege.setProgramEncounterType(groupPrivilegeRequest.getProgramEncounterTypeId() != null ? encounterTypeRepository.findOne(groupPrivilegeRequest.getProgramEncounterTypeId()) : null);
                groupPrivilege.setChecklistDetail(groupPrivilegeRequest.getChecklistDetailId() != null ? checklistDetailRepository.findOne(groupPrivilegeRequest.getChecklistDetailId()) : null);
                groupPrivilege.setAllow(groupPrivilegeRequest.isAllow());

                privilegesToBeAddedOrUpdated.add(groupPrivilege);
            }
        }

        return ResponseEntity.ok(groupPrivilegeRepository.saveAllGroupPrivileges(privilegesToBeAddedOrUpdated));
    }
}
