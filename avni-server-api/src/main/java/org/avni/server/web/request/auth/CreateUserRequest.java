package org.avni.server.web.request.auth;

import java.util.List;

public class CreateUserRequest {
    private String username;
    private String password;
    private String email;
    private String name;
    private String phoneNumber;
    private long organisationId;
    private List<String> userGroupNames;
    private boolean enabled;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(long organisationId) {
        this.organisationId = organisationId;
    }

    public List<String> getUserGroupNames() {
        return userGroupNames;
    }

    public void setUserGroupNames(List<String> userGroupNames) {
        this.userGroupNames = userGroupNames;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
