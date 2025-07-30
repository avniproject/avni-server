package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.domain.metabase.GroupPermissionsBody;
import org.avni.server.util.LogUtil;
import org.avni.server.util.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

@Repository
public class MetabaseConnector {
    protected final RestTemplate restTemplate;
    private static final Logger logger = LoggerFactory.getLogger(MetabaseConnector.class);

    @Value("${metabase.api.url}")
    protected String metabaseApiUrl;

    @Value("${metabase.api.key}")
    protected String apiKey;

    public MetabaseConnector(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    protected HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    protected <T> HttpEntity<T> createHttpEntity(T body) {
        HttpHeaders headers = getHeaders();
        return new HttpEntity<>(body, headers);
    }

    protected void sendPutRequest(String url, Object requestBody) {
        String jsonBody = null;
        try {
            jsonBody = ObjectMapperSingleton.getObjectMapper().writeValueAsString(requestBody);
            HttpEntity<String> entity = createHttpEntity(jsonBody);
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        } catch (Exception e) {
            logger.error(jsonBody);
            throw new RuntimeException("Error serializing request body to JSON", e);
        }
    }

    public <T> T postForObject(String url, Object request, Class<T> responseType) {
        try {
            HttpEntity<Object> entity = createHttpEntity(request);
            return restTemplate.postForObject(url, entity, responseType);
        } catch (RuntimeException e) {
            LogUtil.safeLogError(logger, request);
            throw e;
        }
    }

    protected <T> T getForObject(String url, Class<T> responseType) {
        HttpHeaders headers = getHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType).getBody();
    }

    protected <T> T getForObject(String url, ParameterizedTypeReference responseTypeReference) {
        HttpHeaders headers = getHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return (T) restTemplate.exchange(url, HttpMethod.GET, entity, responseTypeReference).getBody();
    }

    protected <T> T deleteForObject(String url, Class<T> responseType) {
        HttpHeaders headers = getHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, responseType).getBody();
    }

    protected HttpEntity<Map<String, Object>> createJsonEntity(GroupPermissionsBody body) {
        HttpHeaders headers = getHeaders();
        return new HttpEntity<>(body.getBody(), headers);
    }

    protected Map<String, Object> getMapResponse(String url) {
        try {
            String string = getForObject(url, String.class);
            return getObjectMapper().readValue(string, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object getObject(String url, TypeReference clazz) {
        try {
            String jsonResponse = getForObject(url, String.class);
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, clazz);
        } catch (Exception e) {
            logger.error("Get schemas failed for: {}", url);
            throw new RuntimeException(e);
        }
    }
}
