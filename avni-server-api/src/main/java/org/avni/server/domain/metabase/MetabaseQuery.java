package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQuery {
    private final int databaseId;
    private final int sourceTable;
    private final ArrayNode joins;
    private final String type = "query";

    public MetabaseQuery(int databaseId,int sourceTable, ArrayNode joins) {
        this.databaseId = databaseId;
        this.sourceTable = sourceTable;
        this.joins = joins;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.put("source-table", sourceTable);
        queryNode.set("joins", joins);
        queryNode.put("type", type);

        return queryNode;
    }
}
