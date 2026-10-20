package org.avni.server.service.abha;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// What the app needs to open Eka's ABHA screens. accessToken is a bearer credential for the organisation at Eka.
public class AbhaSession {
    private final String accessToken;
    private final String expiresAt;
    private final String environment;
    private final String hipId;

    public AbhaSession(String accessToken, Instant expiresAt, String environment, String hipId) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.SECONDS).toString();
        this.environment = environment;
        this.hipId = hipId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    // ISO-8601 in UTC, to the second, whatever the server's date settings.
    public String getExpiresAt() {
        return expiresAt;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getHipId() {
        return hipId;
    }
}
