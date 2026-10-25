package org.avni.server.service.abdm;

// DEMO-ONLY abdm-hackathon: never merged.
// ABDM v3 paths, from NHA's ABDM-wrapper (v3 GatewayURL) and the M3 sandbox document v2.5. Unverified against the live
// sandbox: field names and paths live here and in the JSON fixtures only, so a correction is one edit.
public final class AbdmPaths {
    private AbdmPaths() {
    }

    // Paths the gateway (or the mock) calls on us, relative to AbdmConfig.publicBaseUrl().
    public static final String IN_HIP_ON_GENERATE_TOKEN = "/api/v3/hip/token/on-generate-token";
    public static final String IN_HIP_ON_LINK_CARE_CONTEXT = "/api/v3/link/on_carecontext";
    public static final String IN_HIP_DISCOVER = "/api/v3/hip/patient/care-context/discover";
    public static final String IN_HIP_LINK_INIT = "/api/v3/hip/link/care-context/init";
    public static final String IN_HIP_LINK_CONFIRM = "/api/v3/hip/link/care-context/confirm";
    public static final String IN_HIP_CONSENT_NOTIFY = "/api/v3/consent/request/hip/notify";
    public static final String IN_HIP_HEALTH_INFORMATION_REQUEST = "/api/v3/hip/health-information/request";
    public static final String IN_HIU_CONSENT_ON_INIT = "/api/v3/hiu/consent/request/on-init";
    public static final String IN_HIU_CONSENT_NOTIFY = "/api/v3/hiu/consent/request/notify";
    public static final String IN_HIU_CONSENT_ON_STATUS = "/api/v3/hiu/consent/request/on-status";
    public static final String IN_HIU_CONSENT_ON_FETCH = "/api/v3/hiu/consent/on-fetch";
    public static final String IN_HIU_HEALTH_INFORMATION_ON_REQUEST = "/api/v3/hiu/health-information/on-request";
    // Our dataPushUrl: an HIP posts encrypted records here. Not a gateway path.
    public static final String IN_HIU_DATA_PUSH = "/api/v3/hiu/health-information/transfer";

    // Paths we call on the gateway, relative to AbdmConfig.gatewayBaseUrl(). The session path is config.
    public static final String OUT_BRIDGE_URL = "/api/hiecm/gateway/v3/bridge/url";
    public static final String OUT_BRIDGE_SERVICES = "/api/hiecm/gateway/v3/bridge-services";
    public static final String OUT_GENERATE_TOKEN = "/api/hiecm/v3/token/generate-token";
    public static final String OUT_LINK_CARE_CONTEXT = "/api/hiecm/hip/v3/link/carecontext";
    public static final String OUT_ON_DISCOVER = "/api/hiecm/user-initiated-linking/v3/patient/care-context/on-discover";
    public static final String OUT_LINK_ON_INIT = "/api/hiecm/user-initiated-linking/v3/link/care-context/on-init";
    public static final String OUT_LINK_ON_CONFIRM = "/api/hiecm/user-initiated-linking/v3/link/care-context/on-confirm";
    public static final String OUT_HIP_CONSENT_ON_NOTIFY = "/api/hiecm/consent/v3/request/hip/on-notify";
    public static final String OUT_HIP_HI_ON_REQUEST = "/api/hiecm/data-flow/v3/health-information/hip/on-request";
    public static final String OUT_HI_NOTIFY = "/api/hiecm/data-flow/v3/health-information/notify";
    public static final String OUT_CONSENT_INIT = "/api/hiecm/consent/v3/request/init";
    public static final String OUT_HIU_CONSENT_ON_NOTIFY = "/api/hiecm/consent/v3/request/hiu/on-notify";
    public static final String OUT_CONSENT_STATUS = "/api/hiecm/consent/v3/request/status";
    public static final String OUT_CONSENT_FETCH = "/api/hiecm/consent/v3/fetch";
    public static final String OUT_HI_REQUEST = "/api/hiecm/data-flow/v3/health-information/request";

    // Headers.
    public static final String H_REQUEST_ID = "REQUEST-ID";
    public static final String H_TIMESTAMP = "TIMESTAMP";
    public static final String H_CM_ID = "X-CM-ID";
    public static final String H_HIP_ID = "X-HIP-ID";
    public static final String H_HIU_ID = "X-HIU-ID";
    public static final String H_LINK_TOKEN = "X-LINK-TOKEN";
}
