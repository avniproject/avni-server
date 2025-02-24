package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserGroupResponse {

    @JsonProperty("email")
    private String email;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("membership_id")
    private Integer membershipId;

    @JsonProperty("group_id")
    private Integer groupId;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("common_name")
    private String commonName;

    public UpdateUserGroupResponse() {
    }

    public UpdateUserGroupResponse(String email, String firstName, Integer membershipId, Integer groupId, Integer id, String lastName, Integer userId, String commonName) {
        this.email = email;
        this.firstName = firstName;
        this.membershipId = membershipId;
        this.groupId = groupId;
        this.id = id;
        this.lastName = lastName;
        this.userId = userId;
        this.commonName = commonName;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public Integer getMembershipId() {
        return membershipId;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public Integer getId() {
        return id;
    }

    public String getLastName() {
        return lastName;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getCommonName() {
        return commonName;
    }
}
