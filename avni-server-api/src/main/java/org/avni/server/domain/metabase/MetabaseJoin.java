package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseJoin {
    private final String fields;
    private final String alias;
    private final int sourceTable;
    private final JsonNode condition;

    public MetabaseJoin(String fields, String alias, int sourceTable, int joinFieldId1, int joinFieldId2, String tableName, ObjectMapper objectMapper) throws Exception {
        this.fields = fields;
        this.alias = alias;
        this.sourceTable = sourceTable;
        this.condition = createConditionNode(joinFieldId1, joinFieldId2, tableName, objectMapper);
    }

    private JsonNode createConditionNode(int joinFieldId1, int joinFieldId2, String tableName, ObjectMapper objectMapper) throws Exception {
        return objectMapper.readTree(
            "[\"=\", [\"field\", " + joinFieldId1 + ", {\"base-type\": \"type/Integer\"}], [\"field\", " + joinFieldId2 + ", {\"base-type\": \"type/Integer\", \"join-alias\": \"" + tableName + "\"}]]"
        );
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
