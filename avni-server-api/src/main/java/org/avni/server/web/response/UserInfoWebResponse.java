package org.avni.server.web.response;

import org.avni.server.domain.JsonObject;
import org.avni.server.web.request.UserInfoContract;

import java.util.List;

public class UserInfoWebResponse extends UserInfoContract {
    private boolean hasAllPrivileges;
    private List<UserPrivilegeWebResponse> privileges;
    private boolean isAdmin;

    private UserInfoWebResponse() {
    }

    public static UserInfoWebResponse createForAdminUser(List<UserPrivilegeWebResponse> groupPrivilegeResponses) {
        UserInfoWebResponse userInfoWebResponse = new UserInfoWebResponse();
        userInfoWebResponse.privileges = groupPrivilegeResponses;
        userInfoWebResponse.isAdmin = true;
        return userInfoWebResponse;
    }

    public UserInfoWebResponse(String username, String orgName, Long orgId, String usernameSuffix, JsonObject settings, String name, String catchmentName, JsonObject syncSettings, List<UserPrivilegeWebResponse> privileges, boolean hasAllPrivileges) {
        super(username, orgName, orgId, usernameSuffix, settings, name, catchmentName, syncSettings);
        this.privileges = privileges;
        this.hasAllPrivileges = hasAllPrivileges;
    }

    public List<UserPrivilegeWebResponse> getPrivileges() {
        return privileges;
    }

    public boolean isHasAllPrivileges() {
        return hasAllPrivileges;
    }

    public boolean getIsAdmin() {
        return isAdmin;
    }
}
