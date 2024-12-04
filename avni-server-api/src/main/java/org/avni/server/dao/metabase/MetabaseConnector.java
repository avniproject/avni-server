package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.GroupPermissionsBody;
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
public class MetabaseConnector {
    protected final RestTemplate restTemplate;

    @Value("${metabase.api.url}")
    protected String metabaseApiUrl;

    @Value("${metabase.api.key}")
    protected String apiKey;

    public MetabaseConnector(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.additionalCustomizers(new RestTemplateStandardCookieCustomizer()).build();
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

    protected void sendPutRequest(String url, Map<String, ?> requestBody) {
        HttpEntity<Map<String, ?>> entity = createHttpEntity(requestBody);
        restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
    }

    public <T> T postForObject(String url, Object request, Class<T> responseType) {
        HttpEntity<Object> entity = createHttpEntity(request);
        return restTemplate.postForObject(url, entity, responseType);
    }

    protected <T> T getForObject(String url, Class<T> responseType) {
        HttpHeaders headers = getHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType).getBody();
    }

    protected HttpEntity<Map<String, Object>> createJsonEntity(GroupPermissionsBody body) {
        HttpHeaders headers = getHeaders();
        return new HttpEntity<>(body.getBody(), headers);
    }

}
