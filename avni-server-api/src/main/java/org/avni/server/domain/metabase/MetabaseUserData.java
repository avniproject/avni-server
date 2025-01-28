package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetabaseUserData {

    @JsonProperty("id")
    private final Integer id;

    @JsonProperty("first_name")
    private final String firstName;

    @JsonProperty("last_name")
    private final String lastName;

    @JsonProperty("email")
    private final String email;

    @JsonProperty("is_active")
    private final boolean isActive;

    @JsonProperty("is_superuser")
    private final String isSuperuser;

    @JsonProperty("group_ids")
    private final List<Integer> groupIds;

    public MetabaseUserData(Integer id, String firstName, String lastName, String email, boolean isActive, String isSuperuser, List<Integer> groupIds) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.isActive = isActive;
        this.isSuperuser = isSuperuser;
        this.groupIds = groupIds;
    }

    public Integer getId() {
        return id;
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

    public boolean isActive() {
        return isActive;
    }

    public String getIsSuperuser() {
        return isSuperuser;
    }

    public List<Integer> getGroupIds() {
        return groupIds;
    }
}
