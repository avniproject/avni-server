package org.avni.server.service;

import com.auth0.jwt.interfaces.Verification;
import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.dao.UserRepository;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

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
