package org.avni.server.web.request.auth;

public class GenerateTokenResult {
    String authToken;

    public GenerateTokenResult(String token) {
        this.authToken = token;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
