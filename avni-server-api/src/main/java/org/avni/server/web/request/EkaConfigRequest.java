package org.avni.server.web.request;

import java.util.List;
import java.util.Map;

public class EkaConfigRequest {
    private String environment;
    private String clientId;
    private String clientSecret;
    private List<String> hipIds;
    private String avniSystemUser;
    private String webhookSigningKey;
    private Map<String, String> hipNames;
    private String hiuId;
    private String clinicId;
    private String doctorOid;
    private String sharingClientId;
    private String sharingClientSecret;

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

    public Map<String, String> getHipNames() {
        return hipNames;
    }

    public void setHipNames(Map<String, String> hipNames) {
        this.hipNames = hipNames;
    }

    public String getHiuId() {
        return hiuId;
    }

    public void setHiuId(String hiuId) {
        this.hiuId = hiuId;
    }

    public String getClinicId() {
        return clinicId;
    }

    public void setClinicId(String clinicId) {
        this.clinicId = clinicId;
    }

    public String getDoctorOid() {
        return doctorOid;
    }

    public void setDoctorOid(String doctorOid) {
        this.doctorOid = doctorOid;
    }

    public String getSharingClientId() {
        return sharingClientId;
    }

    public void setSharingClientId(String sharingClientId) {
        this.sharingClientId = sharingClientId;
    }

    public String getSharingClientSecret() {
        return sharingClientSecret;
    }

    public void setSharingClientSecret(String sharingClientSecret) {
        this.sharingClientSecret = sharingClientSecret;
    }

    // Never print the secrets.
    @Override
    public String toString() {
        return "EkaConfigRequest{environment=" + environment + ", clientId=" + clientId + ", hipIds=" + hipIds
                + ", avniSystemUser=" + avniSystemUser + ", hipNames=" + hipNames + ", hiuId=" + hiuId + ", clinicId=" + clinicId
                + ", doctorOid=" + doctorOid + ", sharingClientId=" + sharingClientId + "}";
    }
}
