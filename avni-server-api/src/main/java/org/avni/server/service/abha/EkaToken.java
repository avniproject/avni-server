package org.avni.server.service.abha;

import java.time.Instant;

public final class EkaToken {
    private final String accessToken;
    private final Instant expiresAt;

    public EkaToken(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    // Never print the token itself.
    @Override
    public String toString() {
        return "EkaToken{expiresAt=" + expiresAt + "}";
    }
}
