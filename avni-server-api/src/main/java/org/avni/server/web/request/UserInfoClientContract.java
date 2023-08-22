package org.avni.server.web.request;

import org.avni.server.domain.JsonObject;
import org.springframework.hateoas.core.Relation;

import java.util.List;

@Relation(collectionRelation = "userInfo")
public class UserInfoClientContract extends UserInfoContract {
    private List<GroupPrivilegeContract> privileges;
    private String[] roles;

    public UserInfoClientContract() {
    }

    public UserInfoClientContract(String username, String orgName, Long orgId, String usernameSuffix, String[] roles, JsonObject settings, String name, String catchmentName, JsonObject syncSettings, List<GroupPrivilegeContract> privileges) {
        super(username, orgName, orgId, usernameSuffix, settings, name, catchmentName, syncSettings);
        this.privileges = privileges;
        this.roles = roles;
    }

    public List<GroupPrivilegeContract> getPrivileges() {
        return privileges;
    }

    public void setPrivileges(List<GroupPrivilegeContract> privileges) {
        this.privileges = privileges;
    }
}
