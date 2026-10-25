package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// What the gateway asks of us as the HIP. Each runs on an AbdmAsync thread, already acting as the organisation's system
// user, after the controller has answered 202.
public interface AbdmHipCallbacks {
    void onGenerateToken(JsonNode body, AbdmCallbackHeaders headers);

    void onLinkCareContext(JsonNode body, AbdmCallbackHeaders headers);

    void discover(JsonNode body, AbdmCallbackHeaders headers);

    void linkInit(JsonNode body, AbdmCallbackHeaders headers);

    void linkConfirm(JsonNode body, AbdmCallbackHeaders headers);

    void consentNotify(JsonNode body, AbdmCallbackHeaders headers);

    void healthInformationRequest(JsonNode body, AbdmCallbackHeaders headers);
}
