package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

public class DatasetRequestBody {

    private final Database database;
    private final TableDetails table;

    public DatasetRequestBody(Database database, TableDetails table) {
        this.database = database;
        this.table = table;
    }

    public ObjectNode toJson() {
        ObjectNode rootNode = ObjectMapperSingleton.getObjectMapper().createObjectNode();
        rootNode.put("database", database.getId());

        ObjectNode queryNode = ObjectMapperSingleton.getObjectMapper().createObjectNode();
        queryNode.put("source-table", table.getId());

        rootNode.set("query", queryNode);
        rootNode.put("type", "query");
        rootNode.set("parameters", ObjectMapperSingleton.getObjectMapper().createArrayNode());

        return rootNode;
    }
}
