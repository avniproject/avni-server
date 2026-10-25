package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// What the gateway (and HIPs, for dataPush) send us as the HIU. Same threading as AbdmHipCallbacks.
public interface AbdmHiuCallbacks {
    void consentRequestOnInit(JsonNode body, AbdmCallbackHeaders headers);

    void consentRequestNotify(JsonNode body, AbdmCallbackHeaders headers);

    void consentRequestOnStatus(JsonNode body, AbdmCallbackHeaders headers);

    void consentOnFetch(JsonNode body, AbdmCallbackHeaders headers);

    void healthInformationOnRequest(JsonNode body, AbdmCallbackHeaders headers);

    void dataPush(JsonNode body, AbdmCallbackHeaders headers);
}
