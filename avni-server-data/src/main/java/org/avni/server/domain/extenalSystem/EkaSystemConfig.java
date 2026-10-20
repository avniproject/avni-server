package org.avni.server.domain.extenalSystem;

import java.util.List;

public class EkaSystemConfig {

    public static final String ENVIRONMENT = "environment";
    public static final String CLIENT_ID = "clientId";
    public static final String ENCRYPTED_CLIENT_SECRET = "encryptedClientSecret";
    public static final String HIP_IDS = "hipIds";
    public static final String AVNI_SYSTEM_USER = "avniSystemUser";
    public static final String ENCRYPTED_WEBHOOK_SIGNING_KEY = "encryptedWebhookSigningKey";

    private static final String SANDBOX = "sandbox";
    private static final String PRODUCTION = "production";
    private static final String SANDBOX_BASE_URL = "https://api.dev.eka.care";
    private static final String PRODUCTION_BASE_URL = "https://api.eka.care";

    private final ExternalSystemConfig externalSystemConfig;

    public EkaSystemConfig(ExternalSystemConfig externalSystemConfig) {
        this.externalSystemConfig = externalSystemConfig;
    }

    public String getEnvironment() {
        return get(ENVIRONMENT);
    }

    public String getBaseUrl() {
        String environment = getEnvironment();
        if (SANDBOX.equals(environment)) {
            return SANDBOX_BASE_URL;
        }
        if (PRODUCTION.equals(environment)) {
            return PRODUCTION_BASE_URL;
        }
        throw new IllegalStateException("Unknown Eka environment: " + environment);
    }

    public String getClientId() {
        return get(CLIENT_ID);
    }

    public String getEncryptedClientSecret() {
        return get(ENCRYPTED_CLIENT_SECRET);
    }

    @SuppressWarnings("unchecked")
    public List<String> getHipIds() {
        return (List<String>) externalSystemConfig.getConfig().getList(HIP_IDS);
    }

    public String getAvniSystemUser() {
        return get(AVNI_SYSTEM_USER);
    }

    public String getEncryptedWebhookSigningKey() {
        return get(ENCRYPTED_WEBHOOK_SIGNING_KEY);
    }

    public Long getOrganisationId() {
        return externalSystemConfig.getOrganisationId();
    }

    private String get(String key) {
        return (String) externalSystemConfig.getConfig().get(key);
    }
}
