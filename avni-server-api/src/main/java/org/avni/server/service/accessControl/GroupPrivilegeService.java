package org.avni.server.service.accessControl;

import org.apache.commons.collections4.IterableUtils;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.NonScopeAwareService;
import org.avni.server.util.CollectionUtil;
import org.avni.server.web.request.GroupPrivilegeContractWeb;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

        /**
         * User Type SubjectTypes, might not have a Registration form Mapping associated with them,
         * still we would like to Show the ViewSubject privilege to be able to sync Subject and its children them to client
         */
        subjectTypeRepository.findAllByIsVoidedFalse().stream()
                .filter(st -> st.getType().equals(Subject.User) && !st.isVoided())
                .forEach(subjectType -> {
                    Privilege viewSubjectPrivilege = privilegeRepository.findByType(PrivilegeType.ViewSubject);
                    GroupPrivilege groupPrivilege = new GroupPrivilege();
                    groupPrivilege.setGroup(group);
                    groupPrivilege.setPrivilege(viewSubjectPrivilege);
                    groupPrivilege.setSubjectType(subjectType);
                    groupPrivilege.assignUUID();
                    allGroupPrivileges.add(groupPrivilege);
                });

        formMappings.forEach(formMapping -> {
            SubjectType subjectType = formMapping.getSubjectType();
            Program program = formMapping.getProgram();
            EncounterType encounterType = formMapping.getEncounterType();
            FormType formType = formMapping.getForm().getFormType();

            if (formType.equals(FormType.IndividualProfile)) {
                privilegeList.stream()
                        .filter(privilege -> privilege.getEntityType() == PrivilegeEntityType.Subject && isGroupSubjectTypePrivilege(subjectType, privilege))
                        .forEach(subjectPrivilege -> {
                                    if (subjectPrivilege.getType() == PrivilegeType.ViewSubject && subjectType.getType().equals(Subject.User) && !subjectType.isVoided()) {
                                        return; //Continue, already added this privilege
                                    }
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

        privilegeList.stream().filter(privilege -> PrivilegeEntityType.NotMappedViaForms.contains(privilege.getEntityType()))
                .forEach(nonFormMappedPrivilege -> {
                    GroupPrivilege groupPrivilege = new GroupPrivilege();
                    groupPrivilege.setGroup(group);
                    groupPrivilege.setPrivilege(nonFormMappedPrivilege);
                    groupPrivilege.assignUUID();
                    allGroupPrivileges.add(groupPrivilege);
                });

        return allGroupPrivileges;
    }

    public void savePrivileges(GroupPrivilegeContractWeb[] requests, Organisation organisation) {
        List<GroupPrivilege> groupPrivileges = groupPrivilegeRepository.findByImplVersion(GroupPrivilege.IMPL_VERSION);
        List<Privilege> privileges = privilegeRepository.findAll();
        List<SubjectType> subjectTypes = subjectTypeRepository.findAll();
        List<Program> programs = programRepository.findAll();
        List<EncounterType> encounterTypes = encounterTypeRepository.findAll();
        List<ChecklistDetail> checklistDetails = checklistDetailRepository.findAll();
        List<Group> groups = groupRepository.findAll();

        Arrays.stream(requests).forEach(request -> {
            try {
                Group targetedGroup = getGroup(request, organisation, groups);
                GroupPrivilege groupPrivilege = groupPrivileges.stream().filter(gp ->
                    Objects.equals(targetedGroup.getUuid(), gp.getGroupUuid())
                    && Objects.equals(request.getPrivilegeUUID(), gp.getPrivilegeUuid())
                    && Objects.equals(request.getSubjectTypeUUID(), gp.getSubjectTypeUuid())
                    && Objects.equals(request.getProgramUUID(), gp.getProgramUuid())
                    && Objects.equals(request.getProgramEncounterTypeUUID(), gp.getProgramEncounterTypeUuid())
                    && Objects.equals(request.getEncounterTypeUUID(), gp.getEncounterTypeUuid())
                    && Objects.equals(request.getChecklistDetailUUID(), gp.getChecklistDetailUuid()))
                    .findAny().orElse(null);
                if (groupPrivilege == null) {
                    groupPrivilege = new GroupPrivilege();
                    //don't use uuid from request for bundle uploads since there could be records with matching uuid with older impl_version in db and unique org_uuid constraint is violated
                    groupPrivilege.assignUUID();
                    groupPrivilege.setPrivilege(CollectionUtil.findByUuid(privileges, request.getPrivilegeUUID()));
                    groupPrivilege.setSubjectType(CollectionUtil.findByUuid(subjectTypes, request.getSubjectTypeUUID()));
                    groupPrivilege.setProgram(CollectionUtil.findByUuid(programs, request.getProgramUUID()));
                    groupPrivilege.setEncounterType(CollectionUtil.findByUuid(encounterTypes, request.getEncounterTypeUUID()));
                    groupPrivilege.setProgramEncounterType(CollectionUtil.findByUuid(encounterTypes, request.getProgramEncounterTypeUUID()));
                    groupPrivilege.setChecklistDetail(CollectionUtil.findByUuid(checklistDetails, request.getChecklistDetailUUID()));
                    groupPrivilege.setGroup(targetedGroup);
                }

                groupPrivilege.setAllow(request.isAllow());
                groupPrivilegeRepository.saveGroupPrivilege(groupPrivilege);
            } catch (Exception e) {
                throw new BulkItemSaveException(request, e);
            }
        });
    }

    private Group getGroup(GroupPrivilegeContractWeb request, Organisation organisation, List<Group> groups) {
        if (request.isNotEveryoneGroup()) {
            return CollectionUtil.findByUuid(groups, request.getGroupUUID());
        } else {
            return groups.stream().filter(x -> Group.Everyone.equals(x.getName())).findFirst().orElse(null);
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return groupPrivilegeRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    /**
     * IMPORTANT : Use this method only if you need explicit Group Privileges specified for the user
     * @param user
     * @return
     */
    public GroupPrivileges getExplicitGroupPrivileges(User user) {
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
