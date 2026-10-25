package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

// DEMO-ONLY abdm-hackathon: never merged.
// Bodies are read as trees, tolerating missing fields, because the v3 field names are not verified against the sandbox.
public final class AbdmJson {
    public static final ObjectMapper M = ObjectMapperSingleton.getObjectMapper();

    private AbdmJson() {
    }

    public static ObjectNode obj() {
        return M.createObjectNode();
    }

    public static ArrayNode arr() {
        return M.createArrayNode();
    }

    // Text at a dotted path ("hiRequest.consent.id"), or null when any step is missing or the value is not text.
    public static String text(JsonNode node, String dottedPath) {
        JsonNode n = at(node, dottedPath);
        return n == null || n.isNull() || n.isMissingNode() || n.isContainerNode() ? null : n.asText();
    }

    public static JsonNode at(JsonNode node, String dottedPath) {
        JsonNode n = node;
        for (String part : dottedPath.split("\\.")) {
            if (n == null) return null;
            n = n.get(part);
        }
        return n;
    }

    // The REQUEST-ID of the request an on-* callback answers.
    public static String requestIdOf(JsonNode body) {
        return text(body, "response.requestId");
    }

    public static String write(JsonNode node) {
        try {
            return M.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("JSON not written", e);
        }
    }

    public static JsonNode read(String json) {
        try {
            return json == null ? null : M.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON not read", e);
        }
    }
}
