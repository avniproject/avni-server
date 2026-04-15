package org.avni.messaging.external;

import org.avni.messaging.config.WatiProperties;
import org.avni.messaging.domain.WatiSystemConfig;
import org.avni.messaging.domain.exception.WatiConnectException;
import org.avni.messaging.domain.exception.WatiNotConfiguredException;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * HTTP client for the Wati WhatsApp Business API.
 *
 * Key difference from GlificRestClient:
 * - Wati uses a static Bearer token — no session/login step needed on every call.
 * - Wati uses plain REST (not GraphQL), so each action has its own URL.
 * - Credentials are fetched from the platform org's external_system_config row
 *   (shared across all Wati-enabled orgs), not the calling org's row.
 */
@Service
public class WatiRestClient {

    private static final Logger logger = LoggerFactory.getLogger(WatiRestClient.class);

    private final RestTemplate restTemplate;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final WatiProperties watiProperties;

    @Autowired
    public WatiRestClient(RestTemplateBuilder builder,
                          ExternalSystemConfigRepository externalSystemConfigRepository,
                          WatiProperties watiProperties) {
        this.restTemplate = builder.build();
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.watiProperties = watiProperties;
    }

    /**
     * Makes a POST call to the Wati API.
     *
     * @param relativeUrl path after the base apiUrl e.g. "/api/v1/sendTemplateMessage/919876543210"
     * @param body        request body object (will be serialised to JSON)
     * @param responseType expected response class
     */
    public <T> T post(String relativeUrl, Object body, Class<T> responseType) {
        WatiSystemConfig config = getSystemConfig();
        String fullUrl = config.getApiUrl() + relativeUrl;
        logger.info("Calling Wati API - POST {}", fullUrl);

        HttpHeaders headers = buildHeaders(config.getApiKey());
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<T> response = restTemplate.exchange(fullUrl, HttpMethod.POST, request, responseType);
            return response.getBody();
        } catch (RestClientException e) {
            logger.error("Error calling Wati API: POST {} — {}", fullUrl, e.getMessage());
            throw new WatiConnectException("Wati API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the shared Wati system config read from the platform org row.
     * Called before every API request to pick up the Bearer token and base URL.
     */
    public WatiSystemConfig getSystemConfig() {
        try {
            return externalSystemConfigRepository.getWatiSystemConfig(watiProperties.getPlatformOrgId());
        } catch (WatiNotConfiguredException e) {
            throw new WatiConnectException("Wati system config not available: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        // Wati expects "Bearer <token>" in the Authorization header.
        // apiKey in config may already include the "Bearer " prefix, so avoid doubling it.
        String authValue = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
        headers.set(HttpHeaders.AUTHORIZATION, authValue);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
