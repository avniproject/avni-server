package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

    @JsonProperty("password")
    private final String password;

    public CreateUserRequest(String firstName, String lastName, String email, List<UserGroupMemberships> userGroupMemberships, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.userGroupMemberships = userGroupMemberships;
        this.password = password;
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

    public String getPassword() {
        return password;
    }
}
