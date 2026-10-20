package org.avni.server.service.abha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Logs in to Eka Care. Every choice below keeps the client secret and tokens out of the logs at any log level:
// - its own RestTemplate, because the shared builder adds an interceptor that logs request headers (the bearer token);
// - the JDK HTTP client, because Apache HttpClient's wire logger prints request bodies at DEBUG;
// - the JSON body written here, because RestTemplate's exchange() logs the body it serialises at DEBUG.
// Never log a request or response body here; both carry secrets.
@Service
public class EkaRestClient {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final String REFRESH_PATH = "/connect-auth/v1/account/refresh-token";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .requestFactory(EkaRestClient::requestFactory)
            .build();

    public EkaToken login(String baseUrl, String clientId, String clientSecret) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        return post(baseUrl + LOGIN_PATH, body, jsonHeaders(), "login");
    }

    public EkaToken refresh(String baseUrl, String clientId, EkaToken current) {
        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", current.getRefreshToken());
        body.put("access_token", current.getAccessToken());
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(current.getAccessToken());
        headers.set("Client-Id", clientId);
        return post(baseUrl + REFRESH_PATH, body, headers, "refresh");
    }

    private EkaToken post(String url, Map<String, String> body, HttpHeaders headers, String call) {
        byte[] json = toJson(body);
        RequestCallback writeBody = request -> {
            request.getHeaders().putAll(headers);
            request.getBody().write(json);
        };
        ResponseExtractor<Map<String, Object>> readBody = response -> objectMapper.readValue(response.getBody(), RESPONSE_TYPE);
        Map<String, Object> response;
        try {
            response = restTemplate.execute(url, HttpMethod.POST, writeBody, readBody);
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

    private static ClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    private static byte[] toJson(Map<String, String> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new EkaAuthException("Could not write the Eka request body", e);
        }
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
