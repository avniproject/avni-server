package org.avni.server.service.abdm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// DEMO-ONLY abdm-hackathon: never merged.
// Every ABDM setting, from application.properties (abdm.*). M2 and M3 go through Eka Care's ABDM Connect, with the
// organisation's Eka settings and token (the M1 ones); Eka calls us back on one webhook, AbdmPaths.IN_EKA_WEBHOOK.
@Component
public class AbdmConfig {
    private final Environment environment;
    private final String publicBaseUrl;
    private final String hipId;
    private final String hipName;
    private final String hiuId;
    private final String organisationUuid;
    private final String systemUser;
    private final String abhaAddressSuffix;
    private final String ekaClinicId;
    private final String ekaDoctorOid;
    private final String ekaRequesterName;
    private final String ekaWebhookVerify;

    public AbdmConfig(Environment environment,
                      @Value("${abdm.public-base-url:}") String publicBaseUrl,
                      @Value("${abdm.hip-id:TANUH}") String hipId,
                      @Value("${abdm.hip-name:TANUH Health Centre}") String hipName,
                      @Value("${abdm.hiu-id:TANUH-HIU}") String hiuId,
                      @Value("${abdm.organisation-uuid:}") String organisationUuid,
                      @Value("${abdm.system-user:}") String systemUser,
                      @Value("${abdm.abha-address-suffix:@sbx}") String abhaAddressSuffix,
                      @Value("${abdm.eka.clinic-id:}") String ekaClinicId,
                      @Value("${abdm.eka.doctor-oid:}") String ekaDoctorOid,
                      @Value("${abdm.eka.requester-name:Dr. Demo}") String ekaRequesterName,
                      @Value("${abdm.eka.webhook-verify:enforce}") String ekaWebhookVerify) {
        this.environment = environment;
        this.publicBaseUrl = publicBaseUrl;
        this.hipId = hipId;
        this.hipName = hipName;
        this.hiuId = hiuId;
        this.organisationUuid = organisationUuid;
        this.systemUser = systemUser;
        this.abhaAddressSuffix = abhaAddressSuffix;
        this.ekaClinicId = ekaClinicId;
        this.ekaDoctorOid = ekaDoctorOid;
        this.ekaRequesterName = ekaRequesterName;
        this.ekaWebhookVerify = ekaWebhookVerify;
    }

    // Where Eka reaches us: the tunnel's address when set, else this server.
    public String publicBaseUrl() {
        return StringUtils.hasText(publicBaseUrl) ? trimSlash(publicBaseUrl) : localBaseUrl();
    }

    // This server as seen from itself. local.server.port is set under RANDOM_PORT tests.
    public String localBaseUrl() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8021"));
        return "http://localhost:" + port;
    }

    public String callbackUrl(String callbackPath) {
        return publicBaseUrl() + callbackPath;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getHipId() { return hipId; }
    public String getHipName() { return hipName; }
    public String getHiuId() { return hiuId; }
    public String getOrganisationUuid() { return organisationUuid; }
    public String getSystemUser() { return systemUser; }
    public String getAbhaAddressSuffix() { return abhaAddressSuffix; }
    public String getEkaClinicId() { return ekaClinicId; }
    public String getEkaDoctorOid() { return ekaDoctorOid; }
    public String getEkaRequesterName() { return ekaRequesterName; }
    public String getEkaWebhookVerify() { return ekaWebhookVerify; }
}
