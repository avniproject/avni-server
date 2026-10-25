package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.avni.server.service.abha.EkaTokenService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka Care's ABDM Connect API (Eka mode), with the current organisation's Eka settings (base URL) and access token, the
// ones M1 uses (ExternalSystemConfigRepository.getEkaSystemConfig, EkaTokenService). Sends Authorization: Bearer plus
// the given headers (X-Pt-Id, X-Partner-Pt-Id, X-Hip-Id ...). Any non-2xx answer throws AbdmGatewayException with Eka's
// error code and message; a call that does not answer throws it with status 0. Never logs a body, a token or an ID of a
// patient. The requestId in the response is a fresh UUID for the traffic log.
// The JDK HTTP client as EkaRestClient uses it: HTTP/1.1, 5 s to connect, 10 s for the whole exchange, a Content-Length
// body. A 401 is not retried: EkaTokenService has no way to drop a token, and renews it 15 minutes before it expires.
@Component
public class EkaAbdmClient {
    private static final Logger logger = LoggerFactory.getLogger(EkaAbdmClient.class);

    public static final String H_PATIENT_OID = "X-Pt-Id";
    public static final String H_PARTNER_PATIENT_ID = "X-Partner-Pt-Id";
    public static final String H_HIP_ID = "X-Hip-Id";

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_MESSAGE = 300;

    private final AbdmOrgContext orgContext;
    private final ExternalSystemConfigRepository configRepository;
    private final EkaTokenService tokenService;
    private final AbdmMessageLog messageLog;
    private final Function<EkaSystemConfig, String> baseUrlOf;
    private final HttpClient httpClient;
    private final Duration answerTimeout;

    @Autowired
    public EkaAbdmClient(AbdmOrgContext orgContext, ExternalSystemConfigRepository configRepository, EkaTokenService tokenService,
                         AbdmMessageLog messageLog) {
        this(orgContext, configRepository, tokenService, messageLog, EkaSystemConfig::getBaseUrl, CONNECT_TIMEOUT, ANSWER_TIMEOUT);
    }

    EkaAbdmClient(AbdmOrgContext orgContext, ExternalSystemConfigRepository configRepository, EkaTokenService tokenService,
                  AbdmMessageLog messageLog, Function<EkaSystemConfig, String> baseUrlOf, Duration connectTimeout, Duration answerTimeout) {
        this.orgContext = orgContext;
        this.configRepository = configRepository;
        this.tokenService = tokenService;
        this.messageLog = messageLog;
        this.baseUrlOf = baseUrlOf;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
        this.answerTimeout = answerTimeout;
    }

    public AbdmGatewayResponse post(String path, JsonNode body, Map<String, String> headers) {
        return exchange("POST", path, body, headers);
    }

    public AbdmGatewayResponse patch(String path, JsonNode body, Map<String, String> headers) {
        return exchange("PATCH", path, body, headers);
    }

    public AbdmGatewayResponse get(String path, Map<String, String> headers) {
        return exchange("GET", path, null, headers);
    }

    // The organisation's Eka base URL, for the admin screen.
    public String baseUrl() {
        return baseUrlOf(config(organisationId()));
    }

    // The organisation's Eka client ID (not a secret), for the admin screen.
    public String clientId() {
        return config(organisationId()).getClientId();
    }

    // Throws when no Eka token can be had for the organisation; for the admin screen.
    public void checkToken() {
        token(organisationId());
    }

    private AbdmGatewayResponse exchange(String method, String path, JsonNode body, Map<String, String> headers) {
        String requestId = UUID.randomUUID().toString();
        String call = "Eka " + method + " " + withoutQuery(path);
        Integer status = null;
        try {
            Long organisationId = organisationId();
            String url = baseUrlOf(config(organisationId)) + path;
            String token = token(organisationId);
            HttpResponse<String> response = send(method, url, token, body, headers, call);
            status = response.statusCode();
            logger.info("{} -> {} (request {})", call, status, requestId);
            JsonNode answer = parse(response.body());
            if (status < 200 || status > 299) throw refusal(call, status, answer);
            return new AbdmGatewayResponse(requestId, status, answer);
        } catch (AbdmGatewayException e) {
            if (status == null) logger.warn("{} failed (request {}): {}", call, requestId, e.getMessage());
            throw e;
        } finally {
            try {
                messageLog.outgoing(method, "eka:" + path, requestId, body, status);
            } catch (RuntimeException e) {
                logger.warn("ABDM message log failed for {}: {}", call, e.getClass().getSimpleName());
            }
        }
    }

    private HttpResponse<String> send(String method, String url, String token, JsonNode body, Map<String, String> headers, String call) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json");
            if (!"GET".equals(method)) builder.header("Content-Type", "application/json");
            if (headers != null) {
                headers.forEach((name, value) -> {
                    if (name != null && value != null && !value.isBlank()) builder.header(name, value);
                });
            }
        } catch (IllegalArgumentException e) {
            throw new AbdmGatewayException(0, null, call + " not sent: the address or a header is not valid");
        }
        HttpRequest.BodyPublisher content = HttpRequest.BodyPublishers.ofString(body == null ? "" : AbdmJson.write(body));
        switch (method) {
            case "GET" -> builder.GET();
            case "PATCH" -> builder.method("PATCH", content);
            default -> builder.POST(content);
        }
        CompletableFuture<HttpResponse<String>> answer = httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
        try {
            return answer.get(answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw new AbdmGatewayException(0, null, call + " did not answer within " + answerTimeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new AbdmGatewayException(0, null, call + " did not answer: " + cause.getClass().getSimpleName());
        } catch (InterruptedException e) {
            answer.cancel(true);
            Thread.currentThread().interrupt();
            throw new AbdmGatewayException(0, null, call + " did not answer: interrupted");
        }
    }

    private Long organisationId() {
        Long organisationId = orgContext.organisationId();
        if (organisationId == null) throw new AbdmGatewayException(0, null, "Eka call not made: no organisation in context");
        return organisationId;
    }

    private EkaSystemConfig config(Long organisationId) {
        try {
            return configRepository.getEkaSystemConfig(organisationId);
        } catch (EkaNotConfiguredException e) {
            throw new AbdmGatewayException(0, null, "Eka is not set up for this organisation: save its Eka settings first");
        }
    }

    private String baseUrlOf(EkaSystemConfig config) {
        try {
            String url = baseUrlOf.apply(config);
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        } catch (IllegalStateException e) {
            throw new AbdmGatewayException(0, null, "Eka settings unusable: " + e.getMessage());
        }
    }

    private String token(Long organisationId) {
        try {
            return tokenService.getAccessToken(organisationId).getAccessToken();
        } catch (EkaNotConfiguredException e) {
            throw new AbdmGatewayException(0, null, "Eka client id or secret not set for this organisation");
        } catch (RuntimeException e) {
            throw new AbdmGatewayException(0, null, "Eka login failed: " + e.getMessage());
        }
    }

    // JSON when the body is JSON, the text when it is not, null when there is none.
    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return AbdmJson.M.readTree(body);
        } catch (Exception e) {
            return TextNode.valueOf(body);
        }
    }

    // Eka's error shapes, read tolerantly: {"code", "error": "<text>", "source_error": {"code": "ABDM-xxxx", "message"}},
    // {"error": {"code", "message"}} and {"data", "error": {}, "success": false}. ABDM's own code wins when present.
    static AbdmGatewayException refusal(String call, int status, JsonNode body) {
        String code = null;
        String message = null;
        String ekaText = null;
        if (body != null && body.isObject()) {
            JsonNode source = body.get("source_error");
            if (source != null && source.isObject()) {
                code = AbdmJson.text(source, "code");
                message = AbdmJson.text(source, "message");
            }
            JsonNode error = body.get("error");
            if (error != null && error.isObject()) {
                if (code == null) code = AbdmJson.text(error, "code");
                if (message == null) message = AbdmJson.text(error, "message");
            } else if (error != null && error.isTextual()) {
                ekaText = error.asText();
            }
            if (code == null) code = AbdmJson.text(body, "code");
            if (message == null) message = AbdmJson.text(body, "message");
        }
        if (message == null) {
            message = ekaText;
            ekaText = null;
        }
        StringBuilder text = new StringBuilder(call).append(" refused with HTTP ").append(status);
        if (code != null) text.append(" ").append(code);
        if (message != null) text.append(": ").append(cut(message));
        if (ekaText != null && !ekaText.equals(message)) text.append(" (").append(cut(ekaText)).append(")");
        return new AbdmGatewayException(status, code, text.toString());
    }

    private static String cut(String s) {
        return s.length() > MAX_MESSAGE ? s.substring(0, MAX_MESSAGE) : s;
    }

    // Patient IDs travel in query strings (?oid=); the app log gets the path only.
    private static String withoutQuery(String path) {
        int q = path == null ? -1 : path.indexOf('?');
        return q < 0 ? path : path.substring(0, q);
    }
}
