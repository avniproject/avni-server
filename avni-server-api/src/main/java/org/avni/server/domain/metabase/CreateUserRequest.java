package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CreateUserRequest {

    @JsonProperty("first_name")
    private final String firstName;

    @JsonProperty("last_name")
    private final String lastName;

    @JsonProperty("email")
    private final String email;

    @JsonProperty("user_group_memberships")
    private final List<UserGroupMemberships> userGroupMemberships;

    public CreateUserRequest(String firstName, String lastName, String email, List<UserGroupMemberships> userGroupMemberships) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.userGroupMemberships = userGroupMemberships;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public List<UserGroupMemberships> getUserGroupMemberships() {
        return userGroupMemberships;
    }
}
