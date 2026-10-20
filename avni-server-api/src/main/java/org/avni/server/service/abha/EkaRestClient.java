package org.avni.server.service.abha;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Logs in to Eka Care. Builds its own RestTemplate: the shared builder adds an interceptor that logs request headers,
// which would print the bearer token. Never log a request or response body here; both carry secrets.
@Service
public class EkaRestClient {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final String REFRESH_PATH = "/connect-auth/v1/account/refresh-token";

    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();

    public EkaToken login(String baseUrl, String clientId, String clientSecret) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        return post(baseUrl + LOGIN_PATH, new HttpEntity<>(body, jsonHeaders()), "login");
    }

    public EkaToken refresh(String baseUrl, String clientId, EkaToken current) {
        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", current.getRefreshToken());
        body.put("access_token", current.getAccessToken());
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(current.getAccessToken());
        headers.set("Client-Id", clientId);
        return post(baseUrl + REFRESH_PATH, new HttpEntity<>(body, headers), "refresh");
    }

    private EkaToken post(String url, HttpEntity<Map<String, String>> request, String call) {
        Map<String, Object> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
            }).getBody();
        } catch (HttpStatusCodeException e) {
            throw new EkaAuthException("Eka " + call + " failed with HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new EkaAuthException("Eka " + call + " did not answer: " + e.getMostSpecificCause().getClass().getSimpleName(), e);
        }
        return toToken(response, call);
    }

    private static EkaToken toToken(Map<String, Object> response, String call) {
        if (response == null) {
            throw new EkaAuthException("Eka " + call + " returned no body");
        }
        Object accessToken = response.get("access_token");
        Object expiresIn = response.get("expires_in");
        if (!(accessToken instanceof String) || ((String) accessToken).isEmpty() || !(expiresIn instanceof Number)) {
            throw new EkaAuthException("Eka " + call + " returned no access_token or expires_in");
        }
        Instant now = Instant.now();
        Object refreshToken = response.get("refresh_token");
        Object refreshExpiresIn = response.get("refresh_expires_in");
        return new EkaToken(
                (String) accessToken,
                now.plusSeconds(((Number) expiresIn).longValue()),
                refreshToken instanceof String ? (String) refreshToken : null,
                refreshExpiresIn instanceof Number ? now.plusSeconds(((Number) refreshExpiresIn).longValue()) : null);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
