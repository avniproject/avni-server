package org.avni.server.domain.extenalSystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EkaSystemConfig {

    public static final String ENVIRONMENT = "environment";
    public static final String CLIENT_ID = "clientId";
    public static final String ENCRYPTED_CLIENT_SECRET = "encryptedClientSecret";
    public static final String HIP_IDS = "hipIds";
    public static final String AVNI_SYSTEM_USER = "avniSystemUser";
    public static final String ENCRYPTED_WEBHOOK_SIGNING_KEY = "encryptedWebhookSigningKey";
    public static final String HIP_NAMES = "hipNames";
    public static final String HIU_ID = "hiuId";
    public static final String CLINIC_ID = "clinicId";
    public static final String DOCTOR_OID = "doctorOid";
    public static final String WEBHOOK_ID = "webhookId";
    public static final String SHARING_CLIENT_ID = "sharingClientId";
    public static final String ENCRYPTED_SHARING_CLIENT_SECRET = "encryptedSharingClientSecret";

    private static final String SANDBOX = "sandbox";
    private static final String PRODUCTION = "production";
    private static final String SANDBOX_BASE_URL = "https://api.dev.eka.care";
    private static final String PRODUCTION_BASE_URL = "https://api.eka.care";
    // ABHA addresses end in the environment's domain (NHA).
    private static final String SANDBOX_ABHA_ADDRESS_SUFFIX = "@sbx";
    private static final String PRODUCTION_ABHA_ADDRESS_SUFFIX = "@abdm";

    private final ExternalSystemConfig externalSystemConfig;

    public EkaSystemConfig(ExternalSystemConfig externalSystemConfig) {
        this.externalSystemConfig = externalSystemConfig;
    }

    public String getEnvironment() {
        return get(ENVIRONMENT);
    }

    public boolean isSandbox() {
        return SANDBOX.equals(getEnvironment());
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

    public String getAbhaAddressSuffix() {
        String environment = getEnvironment();
        if (SANDBOX.equals(environment)) {
            return SANDBOX_ABHA_ADDRESS_SUFFIX;
        }
        if (PRODUCTION.equals(environment)) {
            return PRODUCTION_ABHA_ADDRESS_SUFFIX;
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

    public boolean hasHipId(String hipId) {
        return hipId != null && getHipIds().contains(hipId);
    }

    // The HIP ID given when it is one of the organisation's, else the organisation's first; null when it has none.
    public String hipIdOr(String hipId) {
        if (hasHipId(hipId)) return hipId;
        List<String> hipIds = getHipIds();
        return hipIds.isEmpty() ? null : hipIds.get(0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getHipNames() {
        Object names = externalSystemConfig.getConfig().get(HIP_NAMES);
        return names instanceof Map ? new LinkedHashMap<>((Map<String, String>) names) : Collections.emptyMap();
    }

    // The centre's display name; its ID when it was given none.
    public String getHipName(String hipId) {
        String name = getHipNames().get(hipId);
        return name == null || name.isBlank() ? hipId : name;
    }

    public String getHiuId() {
        return get(HIU_ID);
    }

    public String getClinicId() {
        return get(CLINIC_ID);
    }

    public String getDoctorOid() {
        return get(DOCTOR_OID);
    }

    public String getWebhookId() {
        return get(WEBHOOK_ID);
    }

    public String getSharingClientId() {
        return get(SHARING_CLIENT_ID);
    }

    public String getEncryptedSharingClientSecret() {
        return get(ENCRYPTED_SHARING_CLIENT_SECRET);
    }

    // The phone holds the M1 client's token, so a sharing client that is the M1 client is none.
    public boolean isSharingClientConfigured() {
        return hasText(getSharingClientId()) && hasText(getEncryptedSharingClientSecret()) && !isSharingClientTheM1Client();
    }

    public boolean isSharingClientTheM1Client() {
        return hasText(getSharingClientId()) && getSharingClientId().trim().equals(getClientId() == null ? null : getClientId().trim());
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
