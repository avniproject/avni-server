package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DatasetRequestBody {

    private final Database database;
    private final TableDetails table;

    public DatasetRequestBody(Database database, TableDetails table) {
        this.database = database;
        this.table = table;
    }

    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("database", database.getId());

        ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.put("source-table", table.getId());

        rootNode.set("query", queryNode);
        rootNode.put("type", "query");
        rootNode.set("parameters", objectMapper.createArrayNode());

        return rootNode;
    }
}
