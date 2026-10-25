package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// A gateway answer: the REQUEST-ID we sent, the HTTP status, and the body when there was one (often none: 202).
public record AbdmGatewayResponse(String requestId, int status, JsonNode body) {
}
