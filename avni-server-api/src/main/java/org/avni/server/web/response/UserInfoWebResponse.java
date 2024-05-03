package org.avni.server.web.response;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.metadata.OrganisationCategory;
import org.avni.server.web.request.UserInfoContract;

import java.util.List;

public class UserInfoWebResponse extends UserInfoContract {
    private OrganisationCategory organisationCategory;
    private long lastSessionTime;
    private boolean hasAllPrivileges;
    private List<UserPrivilegeWebResponse> privileges;
    private boolean isAdmin;

    private UserInfoWebResponse() {
    }

    public static UserInfoWebResponse createForAdminUser(List<UserPrivilegeWebResponse> groupPrivilegeResponses, Organisation contextOrganisation, User user) {
        UserInfoWebResponse response = new UserInfoWebResponse();
        response.privileges = groupPrivilegeResponses;
        response.isAdmin = true;
        response.setName(user.getName());
        response.setUsername(user.getUsername());
        response.setLastModifiedDateTime(user.getLastModifiedDateTime());
        if (contextOrganisation != null) {
            response.setOrganisationId(contextOrganisation.getId());
            response.setOrganisationName(contextOrganisation.getName());
            response.setUsernameSuffix(contextOrganisation.getEffectiveUsernameSuffix());
            response.organisationCategory = contextOrganisation.getCategory();
        }
        return response;
    }

    public UserInfoWebResponse(String username, String orgName, Long orgId, String usernameSuffix, JsonObject settings, String name, String catchmentName, JsonObject syncSettings, List<UserPrivilegeWebResponse> privileges, boolean hasAllPrivileges, long lastSessionTime, OrganisationCategory organisationCategory) {
        super(username, orgName, orgId, usernameSuffix, settings, name, catchmentName, syncSettings);
        this.privileges = privileges;
        this.hasAllPrivileges = hasAllPrivileges;
        this.lastSessionTime = lastSessionTime;
        this.organisationCategory = organisationCategory;
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

    public long getLastSessionTime() {
        return lastSessionTime;
    }

    public void setLastSessionTime(long lastSessionTime) {
        this.lastSessionTime = lastSessionTime;
    }

    public OrganisationCategory getOrganisationCategory() {
        return organisationCategory;
    }
}
