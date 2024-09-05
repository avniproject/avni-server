package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQuery {
    private final Database database;
    private final ArrayNode joins;

    public MetabaseQuery(Database database, ArrayNode joins) {
        this.database = database;
        this.joins = joins;
    }

    public int getDatabaseId() {
        return database.getId();
    }


    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.put("database", database.getId());
        queryNode.set("joins", joins);
        queryNode.put("type", "query");
        return queryNode;
    }
}
