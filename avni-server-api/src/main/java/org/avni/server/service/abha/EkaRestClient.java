package org.avni.server.service.abha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Logs in to Eka Care, on the JDK HTTP client directly:
// - its deadline covers the whole exchange, so an answer whose body stalls fails at 10 s too;
// - it writes nothing to Logback at any log level, where RestTemplate and Apache HttpClient print request bodies at DEBUG;
// - it sends a Content-Length, never a chunked body.
// Renewal is a fresh login. Eka's sandbox answers refresh-token without expires_in (checked 20 Oct 2026), so a
// refreshed token's lifetime is unknown. Never log a request or response body here; both carry secrets.
@Service
public class EkaRestClient {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public EkaToken login(String baseUrl, String clientId, String clientSecret) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + LOGIN_PATH))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(toJson(body)))
                .build();
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new EkaAuthException("Eka login failed with HTTP " + response.statusCode());
        }
        return toToken(parse(response.body()));
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        CompletableFuture<HttpResponse<byte[]>> answer = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return answer.get(ANSWER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw new EkaAuthException("Eka login did not answer within " + ANSWER_TIMEOUT.getSeconds() + " s");
        } catch (ExecutionException e) {
            throw new EkaAuthException("Eka login failed: " + e.getCause().getClass().getSimpleName(), e.getCause());
        } catch (InterruptedException e) {
            answer.cancel(true);
            Thread.currentThread().interrupt();
            throw new EkaAuthException("Eka login was interrupted");
        }
    }

    private static Map<String, Object> parse(byte[] body) {
        try {
            return body == null || body.length == 0 ? null : objectMapper.readValue(body, RESPONSE_TYPE);
        } catch (IOException e) {
            throw new EkaAuthException("Eka login returned a body that is not JSON");
        }
    }

    private static EkaToken toToken(Map<String, Object> response) {
        if (response == null) {
            throw new EkaAuthException("Eka login returned no body");
        }
        Object accessToken = response.get("access_token");
        Object expiresIn = response.get("expires_in");
        if (!(accessToken instanceof String) || ((String) accessToken).isEmpty() || !(expiresIn instanceof Number)) {
            throw new EkaAuthException("Eka login returned no access_token or expires_in");
        }
        return new EkaToken((String) accessToken, Instant.now().plusSeconds(((Number) expiresIn).longValue()));
    }

    private static byte[] toJson(Map<String, String> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new EkaAuthException("Could not write the Eka request body", e);
        }
    }
}
