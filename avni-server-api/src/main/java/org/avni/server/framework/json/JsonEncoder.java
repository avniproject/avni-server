package org.avni.server.framework.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.avni.server.util.ObjectMapperSingleton;
import org.owasp.encoder.Encode;

import java.util.Iterator;
import java.util.Map;

public class JsonEncoder {
    public static JsonNode encode(Object object) throws JsonProcessingException {
        return JsonEncoder.encode(ObjectMapperSingleton.getObjectMapper().writeValueAsString(object));
    }

    public static JsonNode encode(String jsonText) throws JsonProcessingException {
        JsonNode jsonNode = ObjectMapperSingleton.getObjectMapper().readTree(jsonText);
        JsonEncoder.encode(jsonNode);
        return jsonNode;
    }

    public static void encode(JsonNode jsonNode) {
        if (jsonNode instanceof ObjectNode)
            JsonEncoder.encode((ObjectNode) jsonNode);
        else if (jsonNode instanceof ArrayNode)
            JsonEncoder.encode((ArrayNode) jsonNode);
    }

    public static void encode(ObjectNode objectNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> childEntry = fields.next();
            JsonNode childValue = childEntry.getValue();
            if (childValue instanceof TextNode) {
                objectNode.put(childEntry.getKey(), Encode.forHtml(childValue.asText()));
            } else if (childValue instanceof ObjectNode) {
                JsonEncoder.encode((ObjectNode) childValue);
            } else if (childValue instanceof ArrayNode) {
                JsonEncoder.encode((ArrayNode) childValue);
            }
        }
    }

    public static void encode(ArrayNode arrayNode) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode childNode = arrayNode.get(i);
            if (childNode instanceof TextNode) {
                arrayNode.set(i, new TextNode(Encode.forHtml(childNode.asText())));
            } else if (childNode instanceof ObjectNode) {
                JsonEncoder.encode((ObjectNode) childNode);
            } else if (childNode instanceof ArrayNode) {
                JsonEncoder.encode((ArrayNode) childNode);
            }
        }
    }
}
