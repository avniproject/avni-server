package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// Calls ABDM's gateway (or the in-process mock) with the session token and the standard headers: Authorization,
// REQUEST-ID, TIMESTAMP, X-CM-ID, plus X-HIP-ID / X-HIU-ID / X-LINK-TOKEN from extraHeaders. Never logs a body.
@Component
public class AbdmGatewayClient {
    // POST to {gatewayBase}{gatewayPath}. requestId becomes the REQUEST-ID header; pass newRequestId() for a fresh one.
    public AbdmGatewayResponse post(String gatewayPath, String requestId, JsonNode body, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public AbdmGatewayResponse patch(String gatewayPath, JsonNode body) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public AbdmGatewayResponse get(String gatewayPath) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    // The HIP's data push to an HIU's dataPushUrl: an absolute URL, no session token, still REQUEST-ID and TIMESTAMP.
    public AbdmGatewayResponse pushToUrl(String absoluteUrl, JsonNode body) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    // M4 registries: an absolute URL with the session token and extraHeaders.
    public AbdmGatewayResponse postAbsolute(String absoluteUrl, JsonNode body, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
