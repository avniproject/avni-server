package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka webhook events for the HIP side. The webhook controller has verified the signature and, on an AbdmAsync thread
// acting as the organisation, passes the whole event envelope.
public interface EkaHipEvents {
    void linkCareContext(JsonNode event);

    void hipDataFetch(JsonNode event);
}
