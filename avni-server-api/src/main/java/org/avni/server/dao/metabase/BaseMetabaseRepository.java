package org.avni.server.dao.metabase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Repository
public class BaseMetabaseRepository {
    protected final RestTemplate restTemplate;

    @Value("${metabase.api.url}")
    protected String metabaseApiUrl;

    @Value("${metabase.api.key}")
    private String apiKey;

    public BaseMetabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    protected <T> HttpEntity<T> createHttpEntity(T body) {
        HttpHeaders headers = getHeaders();
        return new HttpEntity<>(body, headers);
    }

    protected void sendPutRequest(String url, Map<String, Object> requestBody) {
        HttpEntity<Map<String, Object>> entity = createHttpEntity(requestBody);
        restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
    }
}
