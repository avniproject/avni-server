package org.avni.server.web.response.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EnableUserResponse {
    @JsonProperty("Username")
    private String userName;
    @JsonProperty("Success")
    private boolean success;
    @JsonProperty("Error message")
    private String errorMessage;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
