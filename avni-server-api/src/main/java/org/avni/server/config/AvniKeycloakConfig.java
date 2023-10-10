package org.avni.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AvniKeycloakConfig {
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
}
