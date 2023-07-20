package org.avni.server.web.response;

import org.avni.server.domain.NamedEntity;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;

public class UserPrivilegeWebResponse {
    private PrivilegeType privilegeType;
    private String privilegeName;
    private String privilegeEntityType;
    private String privilegeDescription;
    private PrivilegedEntityContract subjectType;
    private PrivilegedEntityContract program;
    private PrivilegedEntityContract programEncounterType;
    private PrivilegedEntityContract encounterType;
    private PrivilegedEntityContract checklistDetail;

    public static UserPrivilegeWebResponse createForOrgUser(GroupPrivilege groupPrivilege) {
        Privilege privilege = groupPrivilege.getPrivilege();
        UserPrivilegeWebResponse webResponse = createFromPrivilege(privilege);

        webResponse.subjectType = PrivilegedEntityContract.create(groupPrivilege.getSubjectType());
        webResponse.program = PrivilegedEntityContract.create(groupPrivilege.getProgram());
        webResponse.programEncounterType = PrivilegedEntityContract.create(groupPrivilege.getProgramEncounterType());
        webResponse.encounterType = PrivilegedEntityContract.create(groupPrivilege.getEncounterType());
        webResponse.checklistDetail = PrivilegedEntityContract.create(groupPrivilege.getChecklistDetail());
        return webResponse;
    }

    private static UserPrivilegeWebResponse createFromPrivilege(Privilege privilege) {
        UserPrivilegeWebResponse webResponse = new UserPrivilegeWebResponse();
        webResponse.setPrivilegeEntityType(privilege.getEntityType().toString());
        webResponse.setPrivilegeName(privilege.getName());
        webResponse.setPrivilegeDescription(privilege.getDescription());
        webResponse.privilegeType = privilege.getType();
        return webResponse;
    }

    public static UserPrivilegeWebResponse createForAdminUser(Privilege privilege) {
        return createFromPrivilege(privilege);
    }

    public String getPrivilegeEntityType() {
        return privilegeEntityType;
    }

    public void setPrivilegeEntityType(String privilegeEntityType) {
        this.privilegeEntityType = privilegeEntityType;
    }

    public String getPrivilegeName() {
        return privilegeName;
    }

    public void setPrivilegeName(String privilegeName) {
        this.privilegeName = privilegeName;
    }

    public String getPrivilegeDescription() {
        return privilegeDescription;
    }

    public void setPrivilegeDescription(String privilegeDescription) {
        this.privilegeDescription = privilegeDescription;
    }

    public PrivilegedEntityContract getSubjectType() {
        return subjectType;
    }

    public PrivilegedEntityContract getProgram() {
        return program;
    }

    public PrivilegedEntityContract getProgramEncounterType() {
        return programEncounterType;
    }

    public PrivilegedEntityContract getEncounterType() {
        return encounterType;
    }

    public PrivilegedEntityContract getChecklistDetail() {
        return checklistDetail;
    }

    public PrivilegeType getPrivilegeType() {
        return privilegeType;
    }

    public static class PrivilegedEntityContract {
        private Long id;
        private String name;

        public static PrivilegedEntityContract create(NamedEntity namedEntity) {
            if (namedEntity == null) return null;
            return new PrivilegedEntityContract(namedEntity.getId(), namedEntity.getName());
        }

        private PrivilegedEntityContract(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
