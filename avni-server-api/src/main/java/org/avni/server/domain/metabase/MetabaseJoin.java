package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseJoin {
    private final String fields;
    private final String alias;
    private final int sourceTable;
    private final JsonNode condition;

    public MetabaseJoin(String fields, String alias, int sourceTable, JsonNode condition) {
        this.fields = fields;
        this.alias = alias;
        this.sourceTable = sourceTable;
        this.condition = condition;
    }

    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode joinNode = objectMapper.createObjectNode();
        joinNode.put("fields", this.fields);
        joinNode.put("alias", this.alias);
        joinNode.put("source-table", this.sourceTable);
        joinNode.set("condition", this.condition);
        return joinNode;
    }
}
