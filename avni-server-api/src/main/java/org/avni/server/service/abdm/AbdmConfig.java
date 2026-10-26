package org.avni.server.service.abdm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// DEMO-ONLY abdm-hackathon: never merged.
// Every ABDM setting, from application.properties (abdm.*). In mock mode the gateway is this server's own /mock-abdm,
// which serves the same paths as NHA's gateway, so the HIP and HIU code is the same in both modes.
@Component
public class AbdmConfig {
    public enum Mode {MOCK, SANDBOX, EKA}

    public static final String MOCK_PREFIX = "/mock-abdm";

    private final Environment environment;
    private final String mode;
    private final String sandboxBaseUrl;
    private final String sessionPath;
    private final String certsPath;
    private final String cmId;
    private final String clientId;
    private final String clientSecret;
    private final String verifyCallbackJwt;
    private final String publicBaseUrl;
    private final String hipId;
    private final String hipName;
    private final String hiuId;
    private final String organisationUuid;
    private final String systemUser;
    private final String demoOtp;
    private final String abhaAddressSuffix;
    private final long mockCallbackDelayMs;
    private final String hfrBaseUrl;
    private final String hfrSearchPath;
    private final String hfrSubmitPath;
    private final String hprBaseUrl;
    private final String hprSearchPath;
    private final String ekaClinicId;
    private final String ekaDoctorOid;
    private final String ekaRequesterName;
    private final boolean ekaSendData;
    private final String ekaWebhookVerify;

    public AbdmConfig(Environment environment,
                      @Value("${abdm.gateway.mode:mock}") String mode,
                      @Value("${abdm.gateway.sandbox-base-url:https://dev.abdm.gov.in}") String sandboxBaseUrl,
                      @Value("${abdm.gateway.session-path:/api/hiecm/gateway/v3/sessions}") String sessionPath,
                      @Value("${abdm.gateway.certs-path:/api/hiecm/gateway/v3/certs}") String certsPath,
                      @Value("${abdm.gateway.cm-id:sbx}") String cmId,
                      @Value("${abdm.gateway.client-id:}") String clientId,
                      @Value("${abdm.gateway.client-secret:}") String clientSecret,
                      @Value("${abdm.gateway.verify-callback-jwt:log}") String verifyCallbackJwt,
                      @Value("${abdm.public-base-url:}") String publicBaseUrl,
                      @Value("${abdm.hip-id:TANUH}") String hipId,
                      @Value("${abdm.hip-name:TANUH Health Centre}") String hipName,
                      @Value("${abdm.hiu-id:TANUH-HIU}") String hiuId,
                      @Value("${abdm.organisation-uuid:}") String organisationUuid,
                      @Value("${abdm.system-user:}") String systemUser,
                      @Value("${abdm.demo-otp:123456}") String demoOtp,
                      @Value("${abdm.abha-address-suffix:@sbx}") String abhaAddressSuffix,
                      @Value("${abdm.mock.callback-delay-ms:300}") long mockCallbackDelayMs,
                      @Value("${abdm.m4.hfr-base-url:https://facilitysbx.abdm.gov.in}") String hfrBaseUrl,
                      @Value("${abdm.m4.hfr-search-path:}") String hfrSearchPath,
                      @Value("${abdm.m4.hfr-submit-path:}") String hfrSubmitPath,
                      @Value("${abdm.m4.hpr-base-url:https://hpridsbx.abdm.gov.in}") String hprBaseUrl,
                      @Value("${abdm.m4.hpr-search-path:}") String hprSearchPath,
                      @Value("${abdm.eka.clinic-id:}") String ekaClinicId,
                      @Value("${abdm.eka.doctor-oid:}") String ekaDoctorOid,
                      @Value("${abdm.eka.requester-name:Dr. Demo}") String ekaRequesterName,
                      @Value("${abdm.eka.send-data:true}") boolean ekaSendData,
                      @Value("${abdm.eka.webhook-verify:enforce}") String ekaWebhookVerify) {
        this.environment = environment;
        this.mode = mode;
        this.sandboxBaseUrl = sandboxBaseUrl;
        this.sessionPath = sessionPath;
        this.certsPath = certsPath;
        this.cmId = cmId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.verifyCallbackJwt = verifyCallbackJwt;
        this.publicBaseUrl = publicBaseUrl;
        this.hipId = hipId;
        this.hipName = hipName;
        this.hiuId = hiuId;
        this.organisationUuid = organisationUuid;
        this.systemUser = systemUser;
        this.demoOtp = demoOtp;
        this.abhaAddressSuffix = abhaAddressSuffix;
        this.mockCallbackDelayMs = mockCallbackDelayMs;
        this.hfrBaseUrl = hfrBaseUrl;
        this.hfrSearchPath = hfrSearchPath;
        this.hfrSubmitPath = hfrSubmitPath;
        this.hprBaseUrl = hprBaseUrl;
        this.hprSearchPath = hprSearchPath;
        this.ekaClinicId = ekaClinicId;
        this.ekaDoctorOid = ekaDoctorOid;
        this.ekaRequesterName = ekaRequesterName;
        this.ekaSendData = ekaSendData;
        this.ekaWebhookVerify = ekaWebhookVerify;
    }

    public Mode getMode() {
        if ("sandbox".equalsIgnoreCase(mode)) return Mode.SANDBOX;
        if ("eka".equalsIgnoreCase(mode)) return Mode.EKA;
        return Mode.MOCK;
    }

    public boolean isMock() {
        return getMode() == Mode.MOCK;
    }

    // M2/M3/M4 through Eka Care's ABDM Connect API, with the organisation's Eka settings and token (the M1 ones),
    // instead of NHA's gateway. Eka calls us back on one webhook, AbdmPaths.IN_EKA_WEBHOOK.
    public boolean isEka() {
        return getMode() == Mode.EKA;
    }

    // Where outgoing gateway calls go.
    public String gatewayBaseUrl() {
        return isMock() ? localBaseUrl() + MOCK_PREFIX : trimSlash(sandboxBaseUrl);
    }

    public String gatewayUrl(String gatewayPath) {
        return gatewayBaseUrl() + gatewayPath;
    }

    // Where the gateway calls us back, and where HIPs push data: the tunnel's address when set, else this server.
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

    public String dataPushUrl() {
        return callbackUrl(AbdmPaths.IN_HIU_DATA_PUSH);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getSandboxBaseUrl() { return trimSlash(sandboxBaseUrl); }
    public String getSessionPath() { return sessionPath; }
    public String getCertsPath() { return certsPath; }
    public String getCmId() { return cmId; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getVerifyCallbackJwt() { return verifyCallbackJwt; }
    public String getConfiguredPublicBaseUrl() { return publicBaseUrl; }
    public String getHipId() { return hipId; }
    public String getHipName() { return hipName; }
    public String getHiuId() { return hiuId; }
    public String getOrganisationUuid() { return organisationUuid; }
    public String getSystemUser() { return systemUser; }
    public String getDemoOtp() { return demoOtp; }
    public String getAbhaAddressSuffix() { return abhaAddressSuffix; }
    public long getMockCallbackDelayMs() { return mockCallbackDelayMs; }
    public String getHfrBaseUrl() { return trimSlash(hfrBaseUrl); }
    public String getHfrSearchPath() { return hfrSearchPath; }
    public String getHfrSubmitPath() { return hfrSubmitPath; }
    public String getHprBaseUrl() { return trimSlash(hprBaseUrl); }
    public String getHprSearchPath() { return hprSearchPath; }
    public String getEkaClinicId() { return ekaClinicId; }
    public String getEkaDoctorOid() { return ekaDoctorOid; }
    public String getEkaRequesterName() { return ekaRequesterName; }
    public boolean isEkaSendData() { return ekaSendData; }
    public String getEkaWebhookVerify() { return ekaWebhookVerify; }
}
