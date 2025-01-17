package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    public MetabaseUserData(Integer id, String firstName, String lastName, String email, boolean isActive, String isSuperuser) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.isActive = isActive;
        this.isSuperuser = isSuperuser;
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

    public String isSuperuser() {
        return isSuperuser;
    }
}
