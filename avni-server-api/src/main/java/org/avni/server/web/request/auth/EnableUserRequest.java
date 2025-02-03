package org.avni.server.web.request.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnableUserRequest {
    @JsonProperty("Username")
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
