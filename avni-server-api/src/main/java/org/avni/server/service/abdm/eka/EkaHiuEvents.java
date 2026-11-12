package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// Eka webhook events for the HIU side. Same threading as EkaHipEvents. AbdmNotReadyException means "not yet": the event
// is tried again later.
public interface EkaHiuEvents {
    void consentUpdate(JsonNode event);

    void hiuDataPush(JsonNode event);
}
