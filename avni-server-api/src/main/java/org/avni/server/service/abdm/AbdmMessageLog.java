package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The abdm_message table: every ABDM message in and out, for the demo's traffic view. Never the session call.
// Swallows its own failures: a log row is never a reason to fail a flow.
@Component
public class AbdmMessageLog {
    public void outgoing(String method, String path, String requestId, JsonNode body, Integer httpStatus) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public void incoming(String path, AbdmCallbackHeaders headers, JsonNode body) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public List<Map<String, Object>> recent(int limit) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
