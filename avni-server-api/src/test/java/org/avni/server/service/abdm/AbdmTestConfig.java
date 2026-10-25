package org.avni.server.service.abdm;

import org.springframework.mock.env.MockEnvironment;

// DEMO-ONLY abdm-hackathon: an AbdmConfig built directly, without Spring, for unit tests.
public final class AbdmTestConfig {
    public static final String CLIENT_ID = "SBXID_TEST";
    public static final String CLIENT_SECRET = "secret-for-test-only";

    private AbdmTestConfig() {
    }

    // Sandbox mode: the gateway is baseUrl.
    public static AbdmConfig sandbox(String baseUrl, String clientSecret) {
        return build("sandbox", baseUrl, clientSecret, "log", 8021, 0);
    }

    // Mock mode: the gateway is this server's /mock-abdm, and the mock calls this server back at localhost:localPort.
    public static AbdmConfig mock(int localPort, long callbackDelayMs) {
        return build("mock", "https://dev.abdm.gov.in", "", "log", localPort, callbackDelayMs);
    }

    public static AbdmConfig build(String mode, String sandboxBaseUrl, String clientSecret, String verifyJwt, int localPort, long callbackDelayMs) {
        MockEnvironment environment = new MockEnvironment().withProperty("server.port", String.valueOf(localPort));
        return new AbdmConfig(environment, mode, sandboxBaseUrl, "/api/hiecm/gateway/v3/sessions", "/api/hiecm/gateway/v3/certs",
                "sbx", CLIENT_ID, clientSecret, verifyJwt, "", "TANUH", "TANUH Health Centre", "TANUH-HIU", "",
                "", "123456", "@sbx", callbackDelayMs, "https://facilitysbx.abdm.gov.in", "", "",
                "https://hpridsbx.abdm.gov.in", "");
    }
}
