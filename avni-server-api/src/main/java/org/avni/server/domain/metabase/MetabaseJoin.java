package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseJoin {
    private String fields;
    private String alias;
    private int sourceTable;
    private JsonNode condition;

    public MetabaseJoin(String fields, String alias, int sourceTable, int joinFieldId1, int joinFieldId2, String tableName, ObjectMapper objectMapper) throws Exception {
        this.fields = fields;
        this.alias = alias;
        this.sourceTable = sourceTable;

        this.condition = createConditionNode(joinFieldId1, joinFieldId2, tableName, BaseType.INTEGER, objectMapper);
    }

    private JsonNode createConditionNode(int joinFieldId1, int joinFieldId2, String tableName, BaseType baseType, ObjectMapper objectMapper) throws Exception {
        ArrayNode conditionNode = objectMapper.createArrayNode();
        conditionNode.add(ConditionType.EQUAL.getOperator());
        conditionNode.add(objectMapper.createArrayNode().add("field").add(joinFieldId1).add(objectMapper.createObjectNode().put("base-type", baseType.getTypeName())));
        conditionNode.add(objectMapper.createArrayNode().add("field").add(joinFieldId2).add(objectMapper.createObjectNode().put("base-type", baseType.getTypeName()).put("join-alias", tableName)));
        return conditionNode;
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
