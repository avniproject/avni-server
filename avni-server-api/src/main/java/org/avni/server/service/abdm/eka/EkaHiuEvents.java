package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka webhook events for the HIU side. Same threading as EkaHipEvents.
public interface EkaHiuEvents {
    void consentUpdate(JsonNode event);

    void hiuDataPush(JsonNode event);
}
