package org.avni.server.service.abdm.gateway;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// Checks the gateway's signature on a callback: the Authorization: Bearer JWT, against the gateway's JWKS at
// {gateway}{abdm.gateway.certs-path}. abdm.gateway.verify-callback-jwt picks what a failure does:
//   off     - nothing is checked;
//   log     - checked and logged as verified / not verified, never refused (skipped in mock mode: the mock signs nothing);
//   enforce - refused with CallbackRejected, which the controller answers with 401. In mock mode, enforce only asks
//             for a Bearer header, because the mock has no keys.
// The JWKS is fetched with the JDK client (5 s) and cached for 10 minutes; a failed fetch is not retried for a minute.
// The token itself is never logged.
@Component
public class AbdmCallbackVerifier {
    private static final Logger logger = LoggerFactory.getLogger(AbdmCallbackVerifier.class);
    private static final Duration JWKS_TTL = Duration.ofMinutes(10);
    private static final Duration JWKS_RETRY = Duration.ofMinutes(1);
    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(5);
    private static final long LEEWAY_SECONDS = 240;

    public static class CallbackRejected extends RuntimeException {
        public CallbackRejected(String message) {
            super(message);
        }
    }

    private final AbdmConfig cfg;
    private final HttpClient httpClient;
    private final Clock clock;
    private Map<String, RSAPublicKey> keys = Map.of();
    private Instant keysFetchedAt;
    private Instant keysFailedAt;

    @Autowired
    public AbdmCallbackVerifier(AbdmConfig cfg) {
        this(cfg, Clock.systemUTC());
    }

    AbdmCallbackVerifier(AbdmConfig cfg, Clock clock) {
        this.cfg = cfg;
        this.clock = clock;
        this.httpClient = AbdmHttp.newClient(JWKS_TIMEOUT);
    }

    public void check(AbdmCallbackHeaders h) {
        String mode = cfg.getVerifyCallbackJwt() == null ? "log" : cfg.getVerifyCallbackJwt().trim().toLowerCase(Locale.ROOT);
        if (mode.equals("off")) return;
        boolean enforce = mode.equals("enforce");
        String token = bearer(h.authorization());
        if (cfg.isMock()) {
            if (enforce && token == null) reject(h, "no Bearer token");
            return;
        }
        if (token == null) {
            if (enforce) reject(h, "no Bearer token");
            logger.warn("ABDM callback {} (request {}) not verified: no Bearer token", h.path(), h.requestId());
            return;
        }
        String failure = verify(token);
        if (failure == null) {
            logger.info("ABDM callback {} (request {}) verified", h.path(), h.requestId());
            return;
        }
        if (enforce) reject(h, failure);
        logger.warn("ABDM callback {} (request {}) not verified: {}", h.path(), h.requestId(), failure);
    }

    private void reject(AbdmCallbackHeaders h, String why) {
        logger.warn("ABDM callback {} (request {}) refused: {}", h.path(), h.requestId(), why);
        throw new CallbackRejected("Callback not verified: " + why);
    }

    private static String bearer(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) value = value.substring(7).trim();
        return value.isEmpty() ? null : value;
    }

    // null when verified, else why not (never the token).
    String verify(String token) {
        DecodedJWT unverified;
        try {
            unverified = JWT.decode(token);
        } catch (RuntimeException e) {
            return "not a JWT";
        }
        RSAPublicKey key = key(unverified.getKeyId());
        if (key == null) return "no gateway key for kid " + unverified.getKeyId();
        try {
            JWT.require(Algorithm.RSA256(key, null)).acceptLeeway(LEEWAY_SECONDS).build().verify(token);
            return null;
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    private synchronized RSAPublicKey key(String kid) {
        Instant now = clock.instant();
        boolean stale = keysFetchedAt == null || now.isAfter(keysFetchedAt.plus(JWKS_TTL));
        boolean unknownKid = kid != null && !keys.containsKey(kid);
        boolean mayFetch = keysFailedAt == null || now.isAfter(keysFailedAt.plus(JWKS_RETRY));
        if ((stale || unknownKid) && mayFetch) {
            try {
                keys = fetchKeys();
                keysFetchedAt = now;
                keysFailedAt = null;
            } catch (RuntimeException e) {
                keysFailedAt = now;
                logger.warn("ABDM gateway keys not fetched: {}", e.getMessage());
            }
        }
        if (kid != null) return keys.get(kid);
        return keys.size() == 1 ? keys.values().iterator().next() : null;
    }

    private Map<String, RSAPublicKey> fetchKeys() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.gatewayUrl(cfg.getCertsPath())))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = AbdmHttp.send(httpClient, request, JWKS_TIMEOUT, "ABDM certs");
        if (!AbdmHttp.ok(response.statusCode())) {
            throw new AbdmGatewayException(response.statusCode(), null, "ABDM certs answered HTTP " + response.statusCode());
        }
        JsonNode jwks = AbdmHttp.parse(response.body());
        JsonNode list = jwks == null ? null : jwks.get("keys");
        Map<String, RSAPublicKey> found = new HashMap<>();
        if (list != null && list.isArray()) {
            for (JsonNode jwk : list) {
                String kty = AbdmJson.text(jwk, "kty");
                String n = AbdmJson.text(jwk, "n");
                String e = AbdmJson.text(jwk, "e");
                if (!"RSA".equalsIgnoreCase(kty) || n == null || e == null) continue;
                String kid = AbdmJson.text(jwk, "kid");
                found.put(kid == null ? "" : kid, rsa(n, e));
            }
        }
        return found;
    }

    static RSAPublicKey rsa(String n, String e) {
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, decoder.decode(n)), new BigInteger(1, decoder.decode(e)));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("ABDM gateway key not readable", ex);
        }
    }
}
