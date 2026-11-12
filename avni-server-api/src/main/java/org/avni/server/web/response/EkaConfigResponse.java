package org.avni.server.web.response;

import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

// The stored Eka settings without any secret; each secret shows only as set or not set.
public class EkaConfigResponse {
    private final String organisationUUID;
    private final String environment;
    private final String clientId;
    private final List<String> hipIds;
    private final String avniSystemUser;
    private final boolean clientSecretSet;
    private final boolean webhookSigningKeySet;
    private final Map<String, String> hipNames;
    private final String hiuId;
    private final String clinicId;
    private final String doctorOid;
    private final String webhookId;
    private final String sharingClientId;
    private final boolean sharingClientSecretSet;

    public EkaConfigResponse(String organisationUUID, EkaSystemConfig config) {
        this.organisationUUID = organisationUUID;
        this.environment = config.getEnvironment();
        this.clientId = config.getClientId();
        this.hipIds = config.getHipIds();
        this.avniSystemUser = config.getAvniSystemUser();
        this.clientSecretSet = StringUtils.hasText(config.getEncryptedClientSecret());
        this.webhookSigningKeySet = StringUtils.hasText(config.getEncryptedWebhookSigningKey());
        this.hipNames = config.getHipNames();
        this.hiuId = config.getHiuId();
        this.clinicId = config.getClinicId();
        this.doctorOid = config.getDoctorOid();
        this.webhookId = config.getWebhookId();
        this.sharingClientId = config.getSharingClientId();
        this.sharingClientSecretSet = StringUtils.hasText(config.getEncryptedSharingClientSecret());
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

    public Map<String, String> getHipNames() {
        return hipNames;
    }

    public String getHiuId() {
        return hiuId;
    }

    public String getClinicId() {
        return clinicId;
    }

    public String getDoctorOid() {
        return doctorOid;
    }

    public String getWebhookId() {
        return webhookId;
    }

    public String getSharingClientId() {
        return sharingClientId;
    }

    public boolean isSharingClientSecretSet() {
        return sharingClientSecretSet;
    }
}
