package org.avni.server.web.response;

import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.springframework.util.StringUtils;

import java.util.List;

// The stored Eka settings without either secret; each secret shows only as set or not set.
public class EkaConfigResponse {
    private final String organisationUUID;
    private final String environment;
    private final String clientId;
    private final List<String> hipIds;
    private final String avniSystemUser;
    private final boolean clientSecretSet;
    private final boolean webhookSigningKeySet;

    public EkaConfigResponse(String organisationUUID, EkaSystemConfig config) {
        this.organisationUUID = organisationUUID;
        this.environment = config.getEnvironment();
        this.clientId = config.getClientId();
        this.hipIds = config.getHipIds();
        this.avniSystemUser = config.getAvniSystemUser();
        this.clientSecretSet = StringUtils.hasText(config.getEncryptedClientSecret());
        this.webhookSigningKeySet = StringUtils.hasText(config.getEncryptedWebhookSigningKey());
    }

    public String getOrganisationUUID() {
        return organisationUUID;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getClientId() {
        return clientId;
    }

    public List<String> getHipIds() {
        return hipIds;
    }

    public String getAvniSystemUser() {
        return avniSystemUser;
    }

    public boolean isClientSecretSet() {
        return clientSecretSet;
    }

    public boolean isWebhookSigningKeySet() {
        return webhookSigningKeySet;
    }
}
