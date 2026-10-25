package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// Calls ABDM's gateway (or the in-process mock) with the session token and the standard headers: Authorization,
// REQUEST-ID, TIMESTAMP, X-CM-ID, plus X-HIP-ID / X-HIU-ID / X-LINK-TOKEN from extraHeaders. Never logs a body.
// A 401 on a session-authenticated call drops the cached session and retries once with the same REQUEST-ID: the
// gateway refused it at the door, so it was never processed.
@Component
public class AbdmGatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(AbdmGatewayClient.class);

    private final AbdmConfig cfg;
    private final AbdmSessionService session;
    private final AbdmMessageLog messageLog;
    private final HttpClient httpClient;
    private final Duration answerTimeout;

    @Autowired
    public AbdmGatewayClient(AbdmConfig cfg, AbdmSessionService session, AbdmMessageLog messageLog) {
        this(cfg, session, messageLog, AbdmHttp.CONNECT_TIMEOUT, AbdmHttp.ANSWER_TIMEOUT);
    }

    AbdmGatewayClient(AbdmConfig cfg, AbdmSessionService session, AbdmMessageLog messageLog, Duration connectTimeout, Duration answerTimeout) {
        this.cfg = cfg;
        this.session = session;
        this.messageLog = messageLog;
        this.httpClient = AbdmHttp.newClient(connectTimeout);
        this.answerTimeout = answerTimeout;
    }

    // POST to {gatewayBase}{gatewayPath}. requestId becomes the REQUEST-ID header; pass newRequestId() for a fresh one.
    public AbdmGatewayResponse post(String gatewayPath, String requestId, JsonNode body, Map<String, String> extraHeaders) {
        return exchange("POST", cfg.gatewayUrl(gatewayPath), gatewayPath, requestId, body, extraHeaders, true);
    }

    public AbdmGatewayResponse patch(String gatewayPath, JsonNode body) {
        return exchange("PATCH", cfg.gatewayUrl(gatewayPath), gatewayPath, newRequestId(), body, null, true);
    }

    public AbdmGatewayResponse get(String gatewayPath) {
        return exchange("GET", cfg.gatewayUrl(gatewayPath), gatewayPath, newRequestId(), null, null, true);
    }

    // The HIP's data push to an HIU's dataPushUrl: an absolute URL, no session token, still REQUEST-ID and TIMESTAMP.
    public AbdmGatewayResponse pushToUrl(String absoluteUrl, JsonNode body) {
        return exchange("POST", absoluteUrl, absoluteUrl, newRequestId(), body, null, false);
    }

    // M4 registries: an absolute URL with the session token and extraHeaders.
    public AbdmGatewayResponse postAbsolute(String absoluteUrl, JsonNode body, Map<String, String> extraHeaders) {
        return exchange("POST", absoluteUrl, absoluteUrl, newRequestId(), body, extraHeaders, true);
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    private AbdmGatewayResponse exchange(String method, String url, String pathForLog, String requestId, JsonNode body,
                                         Map<String, String> extraHeaders, boolean withSession) {
        String id = requestId == null || requestId.isBlank() ? newRequestId() : requestId;
        String call = "ABDM " + method + " " + pathForLog;
        Integer status = null;
        try {
            HttpResponse<String> response = send(method, url, id, body, extraHeaders, withSession, call);
            if (withSession && response.statusCode() == 401) {
                logger.info("{} -> 401 (request {}): renewing the session and retrying once", call, id);
                session.invalidate();
                response = send(method, url, id, body, extraHeaders, true, call);
            }
            status = response.statusCode();
            logger.info("{} -> {} (request {})", call, status, id);
            JsonNode answer = AbdmHttp.parse(response.body());
            if (!AbdmHttp.ok(status)) {
                throw AbdmHttp.refusal(call, status, answer);
            }
            return new AbdmGatewayResponse(id, status, answer);
        } catch (AbdmGatewayException e) {
            if (status == null) logger.warn("{} failed (request {}): {}", call, id, e.getMessage());
            throw e;
        } finally {
            logOutgoing(method, pathForLog, id, body, status);
        }
    }

    private HttpResponse<String> send(String method, String url, String requestId, JsonNode body, Map<String, String> extraHeaders,
                                      boolean withSession, String call) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(url));
            if (withSession) builder.header("Authorization", "Bearer " + session.accessToken());
            builder.header(AbdmPaths.H_REQUEST_ID, requestId)
                    .header(AbdmPaths.H_TIMESTAMP, AbdmTime.now())
                    .header(AbdmPaths.H_CM_ID, cfg.getCmId())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (extraHeaders != null) {
                extraHeaders.forEach((name, value) -> {
                    if (name != null && value != null) builder.header(name, value);
                });
            }
        } catch (IllegalArgumentException e) {
            throw new AbdmGatewayException(0, null, call + " not sent: the address or a header is not valid");
        }
        switch (method) {
            case "GET" -> builder.GET();
            case "PATCH" -> builder.method("PATCH", AbdmHttp.json(body));
            default -> builder.POST(AbdmHttp.json(body));
        }
        return AbdmHttp.send(httpClient, builder.build(), answerTimeout, call);
    }

    private void logOutgoing(String method, String path, String requestId, JsonNode body, Integer status) {
        try {
            messageLog.outgoing(method, path, requestId, body, status);
        } catch (RuntimeException e) {
            logger.warn("ABDM message log failed for {} {}: {}", method, path, e.getClass().getSimpleName());
        }
    }
}
