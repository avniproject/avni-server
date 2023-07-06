package org.avni.server.web.request;

import org.avni.server.domain.JsonObject;

import java.util.List;

public class UserInfoClientContract extends UserInfoContract {
    private List<GroupPrivilegeContract> privileges;

    public UserInfoClientContract() {
    }

    public UserInfoClientContract(String username, String orgName, Long orgId, String usernameSuffix, String[] roles, JsonObject settings, String name, String catchmentName, JsonObject syncSettings, List<GroupPrivilegeContract> privileges) {
        super(username, orgName, orgId, usernameSuffix, roles, settings, name, catchmentName, syncSettings);
        this.privileges = privileges;
    }

    public List<GroupPrivilegeContract> getPrivileges() {
        return privileges;
    }

    public void setPrivileges(List<GroupPrivilegeContract> privileges) {
        this.privileges = privileges;
    }
}
