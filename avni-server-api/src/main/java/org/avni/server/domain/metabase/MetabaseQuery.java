package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQuery {
    private final int sourceTable;
    private final ArrayNode joins;

    public MetabaseQuery(int sourceTable, ArrayNode joins) {
        this.sourceTable = sourceTable;
        this.joins = joins;
    }

    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.put("source-table", this.sourceTable);
        queryNode.set("joins", this.joins);
        return queryNode;
    }
}
