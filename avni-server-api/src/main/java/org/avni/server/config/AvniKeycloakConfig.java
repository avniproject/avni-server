package org.avni.server.config;

import org.avni.server.util.FileUtil;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
public class AvniKeycloakConfig {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AvniKeycloakConfig.class);

    @Value("${avni.keycloak.verify.token.audience}")
    private String verifyTokenAudience;

    @Value("${avni.keycloak.client}")
    private String client;

    @Value("${avni.keycloak.user.email.verified}")
    private String userEmailVerified;

    @Value("${avni.keycloak.user.preferred.username}")
    private String preferredUserName;

    @Value("${avni.keycloak.user.uuid}")
    private String customUserUUID;

    @Value("${avni.keycloak.openid.connect.certs}")
    private String openidConnectCertsUrlFormat;

    @Value("${avni.keycloak.realms}")
    private String realmsUrlFormat;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${avni.keycloak.avni.publicKeyId}")
    private String publicKeyId;

    private String publicKey;

    @PostConstruct
    public void postInit() {
        try {
            publicKey = FileUtil.readStringOfFileOnFileSystem("avniJWKSPublicKey");
        } catch (IOException e) {
            logger.info(String.format("No avni keycloak public key for token encryption found. %s", e.getMessage()));
        }
    }

    public String getVerifyTokenAudience() {
        return verifyTokenAudience;
    }

    public String getUserEmailVerified() {
        return userEmailVerified;
    }

    public String getPreferredUserName() {
        return preferredUserName;
    }

    public String getCustomUserUUID() {
        return customUserUUID;
    }

    public String getOpenidConnectCertsUrlFormat() {
        return openidConnectCertsUrlFormat;
    }

    public String getRealmsUrlFormat() {
        return realmsUrlFormat;
    }

    public String getRealm() {
        return realm;
    }

    public String getClient() {
        return client;
    }

    public String getPublicKeyId() {
        return publicKeyId;
    }

    public String getPublicKey() {
        return publicKey;
    }
}
