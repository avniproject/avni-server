package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// Eka webhook events for the HIU side. Same threading as EkaHipEvents. AbdmNotReadyException means "not yet": the event
// is tried again later.
public interface EkaHiuEvents {
    void consentUpdate(JsonNode event);

    // lastTry: the inbox will not try it again, so a failure that may pass is final.
    void hiuDataPush(JsonNode event, boolean lastTry);

    // Not as the organisation: its system user cannot act. True when all of the ending was applied.
    boolean endConsentWithoutLogin(long organisationId, JsonNode event);
}
