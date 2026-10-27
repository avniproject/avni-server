package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka's answer: the request id we sent, the HTTP status, and the body when there was one (often none: 202).
public record EkaResponse(String requestId, int status, JsonNode body) {
}
