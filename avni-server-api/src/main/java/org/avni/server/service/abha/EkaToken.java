package org.avni.server.service.abha;

import java.time.Instant;

public final class EkaToken {
    private final String accessToken;
    private final Instant expiresAt;
    private final String refreshToken;
    private final Instant refreshExpiresAt;

    public EkaToken(String accessToken, Instant expiresAt, String refreshToken, Instant refreshExpiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.refreshToken = refreshToken;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    // Never print the tokens themselves.
    @Override
    public String toString() {
        return "EkaToken{expiresAt=" + expiresAt + ", refreshExpiresAt=" + refreshExpiresAt + "}";
    }
}
