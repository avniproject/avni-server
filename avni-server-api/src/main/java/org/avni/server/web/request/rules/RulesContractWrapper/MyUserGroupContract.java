package org.avni.server.web.request.rules.RulesContractWrapper;

import org.avni.server.domain.UserGroup;

public class MyUserGroupContract {
    private String uuid;
    private String groupUuid;
    private String groupName;
    private boolean voided;

    public static MyUserGroupContract fromEntity(UserGroup userGroup) {
        MyUserGroupContract contract = new MyUserGroupContract();
        contract.setUuid(userGroup.getUuid());
        contract.setGroupUuid(userGroup.getGroupUuid());
        contract.setGroupName(userGroup.getGroupName());
        contract.setVoided(userGroup.isVoided());
        return contract;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getGroupUuid() {
        return groupUuid;
    }

    public void setGroupUuid(String groupUuid) {
        this.groupUuid = groupUuid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }
}
