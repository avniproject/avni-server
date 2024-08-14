package org.avni.server.web.request;

import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.PrivilegeType;

public class GroupPrivilegeBundleContract extends GroupPrivilegeContractWeb {
    private PrivilegeType privilegeType;

    public PrivilegeType getPrivilegeType() {
        return privilegeType;
    }

    public void setPrivilegeType(PrivilegeType privilegeType) {
        this.privilegeType = privilegeType;
    }

    public static GroupPrivilegeBundleContract fromEntity(GroupPrivilege groupPrivilege) {
        GroupPrivilegeBundleContract groupPrivilegeContractWeb = new GroupPrivilegeBundleContract();
        groupPrivilegeContractWeb.setUuid(groupPrivilege.getUuid());
        groupPrivilegeContractWeb.setGroupUUID(groupPrivilege.getGroupUuid());
        groupPrivilegeContractWeb.setPrivilegeUUID(groupPrivilege.getPrivilegeUuid());
        groupPrivilegeContractWeb.setPrivilegeType(groupPrivilege.getPrivilege().getType());
        groupPrivilegeContractWeb.setSubjectTypeUUID(groupPrivilege.getSubjectTypeUuid());
        groupPrivilegeContractWeb.setProgramUUID(groupPrivilege.getProgramUuid());
        groupPrivilegeContractWeb.setProgramEncounterTypeUUID(groupPrivilege.getProgramEncounterTypeUuid());
        groupPrivilegeContractWeb.setEncounterTypeUUID(groupPrivilege.getEncounterTypeUuid());
        groupPrivilegeContractWeb.setChecklistDetailUUID(groupPrivilege.getChecklistDetailUuid());
        groupPrivilegeContractWeb.setAllow(groupPrivilege.isAllow());
        groupPrivilegeContractWeb.setVoided(groupPrivilege.isVoided());
        groupPrivilegeContractWeb.setNotEveryoneGroup(!groupPrivilege.getGroup().isEveryone());
        return groupPrivilegeContractWeb;
    }

}
