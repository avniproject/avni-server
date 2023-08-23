package org.avni.server.service.accessControl;

import org.apache.commons.collections4.IterableUtils;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.NonScopeAwareService;
import org.avni.server.web.request.GroupPrivilegeContractWeb;
import org.springframework.stereotype.Service;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

@Service
public class GroupPrivilegeService implements NonScopeAwareService {
    private final GroupRepository groupRepository;
    private final PrivilegeRepository privilegeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final FormMappingRepository formMappingRepository;
    private final GroupPrivilegeRepository groupPrivilegeRepository;
    private final UserGroupRepository userGroupRepository;

    public GroupPrivilegeService(GroupRepository groupRepository, PrivilegeRepository privilegeRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, ChecklistDetailRepository checklistDetailRepository, FormMappingRepository formMappingRepository, GroupPrivilegeRepository groupPrivilegeRepository, UserGroupRepository userGroupRepository) {
        this.groupRepository = groupRepository;
        this.privilegeRepository = privilegeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.formMappingRepository = formMappingRepository;
        this.groupPrivilegeRepository = groupPrivilegeRepository;
        this.userGroupRepository = userGroupRepository;
    }

    private boolean isGroupSubjectTypePrivilege(SubjectType subjectType, Privilege privilege) {
        if (!subjectType.isGroup()) {
            return !privilege.getType().isForGroupSubject();
        }
        return true;
    }

    public List<GroupPrivilege> getAllGroupPrivileges(long groupId) {
        List<FormMapping> formMappings = formMappingRepository.findAllByIsVoidedFalse();
        List<ChecklistDetail> checklistDetails = checklistDetailRepository.findAllByOrganisationId(UserContextHolder.getUserContext().getOrganisationId());

        Group group = groupRepository.findOne(groupId);
        List<Privilege> privilegeList = IterableUtils.toList(privilegeRepository.findAllByIsVoidedFalse());

        List<GroupPrivilege> allGroupPrivileges = new ArrayList<>();

        formMappings.forEach(formMapping -> {
            SubjectType subjectType = formMapping.getSubjectType();
            Program program = formMapping.getProgram();
            EncounterType encounterType = formMapping.getEncounterType();
            FormType formType = formMapping.getForm().getFormType();

            if (formType.equals(FormType.IndividualProfile)) {
                privilegeList.stream()
                        .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Subject && isGroupSubjectTypePrivilege(subjectType, privilege))
                        .forEach(subjectPrivilege -> {
                                    GroupPrivilege groupPrivilege = new GroupPrivilege();
                                    groupPrivilege.setGroup(group);
                                    groupPrivilege.setPrivilege(subjectPrivilege);
                                    groupPrivilege.setSubjectType(subjectType);
                                    groupPrivilege.assignUUID();
                                    allGroupPrivileges.add(groupPrivilege);
                                }
                        );
            }

            if (formType.equals(FormType.ProgramEnrolment)) {
                privilegeList.stream()
                        .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Enrolment)
                        .forEach(enrolmentPrivilege -> {
                            GroupPrivilege groupPrivilege = new GroupPrivilege();
                            groupPrivilege.setGroup(group);
                            groupPrivilege.setPrivilege(enrolmentPrivilege);
                            groupPrivilege.setSubjectType(subjectType);
                            groupPrivilege.setProgram(program);
                            groupPrivilege.assignUUID();
                            allGroupPrivileges.add(groupPrivilege);
                        });

                checklistDetails.forEach(checklistDetail ->
                        privilegeList.stream()
                                .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Checklist)
                                .forEach(privilege -> {
                                    GroupPrivilege groupPrivilege = new GroupPrivilege();
                                    groupPrivilege.setGroup(group);
                                    groupPrivilege.setPrivilege(privilege);
                                    groupPrivilege.setSubjectType(subjectType);
                                    groupPrivilege.setChecklistDetail(checklistDetail);
                                    groupPrivilege.setAllow(false);
                                    groupPrivilege.assignUUID();
                                    allGroupPrivileges.add(groupPrivilege);
                                })
                );
            }

            if (formType.equals(FormType.ProgramEncounter)) {
                privilegeList.stream()
                        .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Encounter)
                        .forEach(encounterPrivilege -> {
                            GroupPrivilege groupPrivilege = new GroupPrivilege();
                            groupPrivilege.setGroup(group);
                            groupPrivilege.setPrivilege(encounterPrivilege);
                            groupPrivilege.setSubjectType(subjectType);
                            groupPrivilege.setProgram(program);
                            groupPrivilege.setProgramEncounterType(encounterType);
                            groupPrivilege.setAllow(false);
                            groupPrivilege.assignUUID();
                            allGroupPrivileges.add(groupPrivilege);
                        });
            }

            if (formType.equals(FormType.Encounter)) {
                privilegeList.stream()
                        .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Encounter)
                        .forEach(encounterPrivilege -> {
                            GroupPrivilege groupPrivilege = new GroupPrivilege();
                            groupPrivilege.setGroup(group);
                            groupPrivilege.setPrivilege(encounterPrivilege);
                            groupPrivilege.setSubjectType(subjectType);
                            groupPrivilege.setEncounterType(formMapping.getEncounterType());
                            groupPrivilege.assignUUID();
                            allGroupPrivileges.add(groupPrivilege);
                        });
            }
        });

        privilegeList.stream().filter(privilege -> PrivilegeEntityType.NotMappingViaForms.contains(privilege.getEntityType()))
                .forEach(nonFormMappedPrivilege -> {
                    GroupPrivilege groupPrivilege = new GroupPrivilege();
                    groupPrivilege.setGroup(group);
                    groupPrivilege.setPrivilege(nonFormMappedPrivilege);
                    groupPrivilege.assignUUID();
                    allGroupPrivileges.add(groupPrivilege);
                });

        return allGroupPrivileges;
    }


    public void uploadPrivileges(GroupPrivilegeContractWeb request, Organisation organisation) {
        GroupPrivilege groupPrivilege = groupPrivilegeRepository.findByUuid(request.getUuid());
        if (groupPrivilege == null) {
            groupPrivilege = new GroupPrivilege();
        }
        groupPrivilege.setUuid(request.getUuid());
        groupPrivilege.setPrivilege(privilegeRepository.findByUuid(request.getPrivilegeUUID()));
        groupPrivilege.setGroup(getCorrespondingGroup(request, organisation));
        groupPrivilege.setSubjectType(subjectTypeRepository.findByUuid(request.getSubjectTypeUUID()));
        groupPrivilege.setProgram(programRepository.findByUuid(request.getProgramUUID()));
        groupPrivilege.setEncounterType(encounterTypeRepository.findByUuid(request.getEncounterTypeUUID()));
        groupPrivilege.setProgramEncounterType(encounterTypeRepository.findByUuid(request.getProgramEncounterTypeUUID()));
        groupPrivilege.setChecklistDetail(checklistDetailRepository.findByUuid(request.getChecklistDetailUUID()));
        groupPrivilege.setAllow(request.isAllow());
        groupPrivilegeRepository.save(groupPrivilege);
    }

    private Group getCorrespondingGroup(GroupPrivilegeContractWeb request, Organisation organisation) {
        if (request.isNotEveryoneGroup()) {
            return groupRepository.findByUuid(request.getGroupUUID());
        } else {
            return groupRepository.findByNameAndOrganisationId(Group.Everyone, organisation.getId());
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return groupPrivilegeRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public GroupPrivileges getGroupPrivileges(User user) {
        List<GroupPrivilege> privileges = groupPrivilegeRepository.getAllAllowedPrivilegesForUser(user.getId());
        return new GroupPrivileges(false, privileges);
    }

    public GroupPrivileges getGroupPrivileges() {
        if (this.userHasAllPrivileges()) {
            return new GroupPrivileges();
        }
        User user = UserContextHolder.getUserContext().getUser();
        List<GroupPrivilege> privileges = groupPrivilegeRepository.getAllAllowedPrivilegesForUser(user.getId());
        return new GroupPrivileges(false, privileges);
    }

    public boolean userHasAllPrivileges() {
        User user = UserContextHolder.getUserContext().getUser();
        return userGroupRepository.findByUserAndGroupHasAllPrivilegesTrueAndIsVoidedFalse(user).size() > 0;
    }
}
