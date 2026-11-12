package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;

// Eka webhook events for the HIP side. The webhook controller has verified the signature and stored the event; the
// worker (EkaEventWorker), acting as the organisation, passes the whole event envelope. A handler that throws is tried
// again later, so a handler must be safe to run twice.
public interface EkaHipEvents {
    void linkCareContext(JsonNode event);

    void hipDataFetch(JsonNode event);
}
