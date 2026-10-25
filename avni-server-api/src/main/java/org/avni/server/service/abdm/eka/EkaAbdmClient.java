package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka Care's ABDM Connect API (Eka mode), with the current organisation's Eka settings (base URL) and access token, the
// ones M1 uses (ExternalSystemConfigRepository.getEkaSystemConfig, EkaTokenService). Sends Authorization: Bearer plus
// the given headers (X-Pt-Id, X-Partner-Pt-Id, X-Hip-Id ...). Any non-2xx answer throws AbdmGatewayException with Eka's
// error code and message; a call that does not answer throws it with status 0. Never logs a body, a token or an ID of a
// patient. The requestId in the response is a fresh UUID for the traffic log.
@Component
public class EkaAbdmClient {
    public static final String H_PATIENT_OID = "X-Pt-Id";
    public static final String H_PARTNER_PATIENT_ID = "X-Partner-Pt-Id";
    public static final String H_HIP_ID = "X-Hip-Id";

    public AbdmGatewayResponse post(String path, JsonNode body, Map<String, String> headers) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public AbdmGatewayResponse patch(String path, JsonNode body, Map<String, String> headers) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public AbdmGatewayResponse get(String path, Map<String, String> headers) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
