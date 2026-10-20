package org.avni.server.web.request;

import java.util.List;

public class EkaConfigRequest {
    private String environment;
    private String clientId;
    private String clientSecret;
    private List<String> hipIds;
    private String avniSystemUser;
    private String webhookSigningKey;

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getHipIds() {
        return hipIds;
    }

    public void setHipIds(List<String> hipIds) {
        this.hipIds = hipIds;
    }

    public String getAvniSystemUser() {
        return avniSystemUser;
    }

    public void setAvniSystemUser(String avniSystemUser) {
        this.avniSystemUser = avniSystemUser;
    }

    public String getWebhookSigningKey() {
        return webhookSigningKey;
    }

    public void setWebhookSigningKey(String webhookSigningKey) {
        this.webhookSigningKey = webhookSigningKey;
    }

    // Never print the secrets.
    @Override
    public String toString() {
        return "EkaConfigRequest{environment=" + environment + ", clientId=" + clientId + ", hipIds=" + hipIds
                + ", avniSystemUser=" + avniSystemUser + "}";
    }
}
