package org.avni.server.service;

import com.auth0.jwt.interfaces.Verification;
import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.auth.KeycloakResponse;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnExpression("'${avni.idp.type}'=='keycloak' or '${avni.idp.type}'=='both'")
public class KeycloakAuthService extends BaseIAMService {
    private final Logger logger = LoggerFactory.getLogger(KeycloakAuthService.class);
    private final AdapterConfig adapterConfig;
    private final AvniKeycloakConfig avniKeycloakConfig;

    @Autowired
    public KeycloakAuthService(UserRepository userRepository, AdapterConfig adapterConfig, AvniKeycloakConfig avniKeycloakConfig) {
        super(userRepository);
        this.adapterConfig = adapterConfig;
        this.avniKeycloakConfig = avniKeycloakConfig;
    }

    @Override
    public void logConfiguration() {
        logger.debug("Keycloak configuration");
        logger.debug(String.format("Keycloak server: %s", adapterConfig.getAuthServerUrl()));
        logger.debug(String.format("Realm name: %s", adapterConfig.getRealm()));
        logger.debug(String.format("Audience name: %s", adapterConfig.getResource()));
    }

    @Override
    public String generateTokenForUser(String username, String password) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", avniKeycloakConfig.getClient());
        map.add("grant_type", "password");
        map.add("scope", "openid");
        map.add("username", username);
        map.add("password", password);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        ResponseEntity<KeycloakResponse> responseEntity =
                restTemplate.exchange(String.format("%s/realms/%s/protocol/openid-connect/token", adapterConfig.getAuthServerUrl(), adapterConfig.getRealm()),
                        HttpMethod.POST,
                        entity,
                        KeycloakResponse.class);
        KeycloakResponse keycloakResponse = responseEntity.getBody();
        return keycloakResponse.getAccessToken();
    }

    protected String getJwkProviderUrl() {
        return String.format(avniKeycloakConfig.getOpenidConnectCertsUrlFormat(), getIssuer());
    }

    protected String getIssuer() {
        return String.format(avniKeycloakConfig.getRealmsUrlFormat(), adapterConfig.getAuthServerUrl(), adapterConfig.getRealm());
    }

    @Override
    protected String getUserUuidField() {
        return avniKeycloakConfig.getCustomUserUUID();
    }

    @Override
    protected String getUsernameField() {
        return avniKeycloakConfig.getPreferredUserName();
    }

    @Override
    protected void addClaim(Verification verification) {
        verification.withClaim(avniKeycloakConfig.getUserEmailVerified(), true);
    }

    @Override
    protected String getAudience() {
        return avniKeycloakConfig.getVerifyTokenAudience();
    }
}
