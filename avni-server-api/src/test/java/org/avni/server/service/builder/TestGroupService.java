package org.avni.server.service.builder;

import org.avni.server.dao.GroupPrivilegeRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component
public class TestGroupService {
    private final GroupRepository groupRepository;
    private final GroupPrivilegeRepository groupPrivilegeRepository;
    private final PrivilegeRepository privilegeRepository;

    @Autowired
    public TestGroupService(GroupRepository groupRepository, GroupPrivilegeRepository groupPrivilegeRepository, PrivilegeRepository privilegeRepository) {
        this.groupRepository = groupRepository;
        this.groupPrivilegeRepository = groupPrivilegeRepository;
        this.privilegeRepository = privilegeRepository;
    }

    public void updateGroup(Group group, Map<GroupPrivilege, PrivilegeType> groupPrivileges) {
        groupPrivileges.forEach((groupPrivilege, privilegeType) -> {
            givePrivilege(group, groupPrivilege, privilegeType);
        });
    }

    private void givePrivilege(Group group, GroupPrivilege groupPrivilege, PrivilegeType privilegeType) {
        Privilege p = privilegeRepository.findByType(privilegeType);
        groupPrivilege.setPrivilege(p);
        groupPrivilege.setGroup(group);
        groupPrivilegeRepository.saveGroupPrivilege(groupPrivilege);
    }

    public void giveViewSubjectPrivilegeTo(Group group, SubjectType ... subjectTypes) {
        Arrays.stream(subjectTypes).forEach(subjectType -> {
            this.givePrivilege(group, new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().setSubjectType(subjectType).build(), PrivilegeType.ViewSubject);
        });
    }

    public void giveEditSubjectPrivilegeTo(Group group, SubjectType ... subjectTypes) {
        Arrays.stream(subjectTypes).forEach(subjectType -> {
            this.givePrivilege(group, new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().setSubjectType(subjectType).build(), PrivilegeType.EditSubject);
        });
    }

    public void giveViewProgramPrivilegeTo(Group group, SubjectType subjectType, Program... programs) {
        Arrays.stream(programs).forEach(program -> {
            this.givePrivilege(group, new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().setSubjectType(subjectType).setProgram(program).build(), PrivilegeType.ViewEnrolmentDetails);
        });
    }

    public void giveMultiTxEntityTypeUpdatePrivilegeTo(Group group) {
        this.givePrivilege(group, new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.MultiTxEntityTypeUpdate);
    }
}
