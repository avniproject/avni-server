package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// The bridge's gateway session token, cached until close to expiry. Never logs the token or the client secret.
// One token for the whole server; renewed when under two minutes remain; after a failure, callers get that failure for
// ten seconds rather than a fresh login attempt each, so a wrong secret does not hammer the gateway.
@Component
public class AbdmSessionService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmSessionService.class);
    static final Duration RENEW_BEFORE = Duration.ofMinutes(2);
    static final Duration BACK_OFF = Duration.ofSeconds(10);
    static final long DEFAULT_EXPIRES_IN_SECONDS = 1200;

    private final AbdmConfig cfg;
    private final HttpClient httpClient;
    private final Duration answerTimeout;
    private final Clock clock;

    private String token;
    private Instant expiresAt;
    private Instant failedAt;
    private AbdmGatewayException lastFailure;

    @Autowired
    public AbdmSessionService(AbdmConfig cfg) {
        this(cfg, AbdmHttp.CONNECT_TIMEOUT, AbdmHttp.ANSWER_TIMEOUT, Clock.systemUTC());
    }

    AbdmSessionService(AbdmConfig cfg, Duration connectTimeout, Duration answerTimeout, Clock clock) {
        this.cfg = cfg;
        this.httpClient = AbdmHttp.newClient(connectTimeout);
        this.answerTimeout = answerTimeout;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(expiresAt.minus(RENEW_BEFORE))) return token;
        if (!cfg.isMock() && !StringUtils.hasText(cfg.getClientSecret())) {
            throw new AbdmGatewayException(0, null, "ABDM_CLIENT_SECRET not set");
        }
        if (failedAt != null && now.isBefore(failedAt.plus(BACK_OFF))) {
            if (stillValid(now)) return token;
            throw lastFailure;
        }
        try {
            fetch(now);
            failedAt = null;
            lastFailure = null;
            return token;
        } catch (AbdmGatewayException e) {
            failedAt = now;
            lastFailure = e;
            logger.warn("ABDM session not renewed: HTTP {}{}", e.getStatus(), e.getAbdmCode() == null ? "" : " " + e.getAbdmCode());
            if (stillValid(now)) return token;
            token = null;
            expiresAt = null;
            throw e;
        }
    }

    public synchronized void invalidate() {
        token = null;
        expiresAt = null;
    }

    private boolean stillValid(Instant now) {
        return token != null && now.isBefore(expiresAt);
    }

    private void fetch(Instant now) {
        String url = cfg.gatewayBaseUrl() + cfg.getSessionPath();
        JsonNode body = AbdmJson.obj()
                .put("clientId", cfg.getClientId())
                .put("clientSecret", cfg.getClientSecret())
                .put("grantType", "client_credentials");
        String requestId = UUID.randomUUID().toString();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .header(AbdmPaths.H_REQUEST_ID, requestId)
                    .header(AbdmPaths.H_TIMESTAMP, AbdmTime.now())
                    .header(AbdmPaths.H_CM_ID, cfg.getCmId())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(AbdmHttp.json(body))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new AbdmGatewayException(0, null, "ABDM session not asked for: the gateway address or a header is not valid");
        }
        HttpResponse<String> response = AbdmHttp.send(httpClient, request, answerTimeout, "ABDM session");
        JsonNode answer = AbdmHttp.parse(response.body());
        logger.info("ABDM session POST {} -> {} (request {})", cfg.getSessionPath(), response.statusCode(), requestId);
        if (!AbdmHttp.ok(response.statusCode())) {
            throw scrubbed(AbdmHttp.refusal("ABDM session", response.statusCode(), answer));
        }
        String accessToken = AbdmJson.text(answer, "accessToken");
        if (!StringUtils.hasText(accessToken)) {
            throw new AbdmGatewayException(response.statusCode(), null, "ABDM session answered without an accessToken");
        }
        JsonNode expiresIn = AbdmJson.at(answer, "expiresIn");
        long seconds = expiresIn != null && expiresIn.canConvertToLong() && expiresIn.asLong() > 0 ? expiresIn.asLong() : DEFAULT_EXPIRES_IN_SECONDS;
        this.token = accessToken;
        this.expiresAt = now.plusSeconds(seconds);
    }

    // A gateway that echoes the request must not put the secret into an exception message, which is shown and logged.
    private AbdmGatewayException scrubbed(AbdmGatewayException e) {
        String secret = cfg.getClientSecret();
        if (!StringUtils.hasText(secret) || e.getMessage() == null || !e.getMessage().contains(secret)) return e;
        return new AbdmGatewayException(e.getStatus(), e.getAbdmCode(), e.getMessage().replace(secret, "***"));
    }
}
