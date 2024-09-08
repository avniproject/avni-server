package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQueryBuilder {
    private final Database database;
    private final ArrayNode joinsArray;
    private final ObjectMapper objectMapper;
    private ObjectNode queryNode;

    public MetabaseQueryBuilder(Database database, ArrayNode joinsArray, ObjectMapper objectMapper) {
        this.database = database;
        this.joinsArray = joinsArray;
        this.objectMapper = objectMapper;
        this.queryNode = objectMapper.createObjectNode();
    }

    public MetabaseQueryBuilder forTable(TableDetails tableDetails) {
        queryNode.put("source-table", tableDetails.getId());
        return this;
    }

    public MetabaseQueryBuilder joinWith(TableDetails addressTable, FieldDetails joinField1, FieldDetails joinField2) {
        ObjectNode joinNode = objectMapper.createObjectNode();
        joinNode.put("fields", "all");
        joinNode.put("alias", addressTable.getName());
        joinNode.put("source-table", addressTable.getId());

        ArrayNode conditionArray = objectMapper.createArrayNode();
        conditionArray.add("=");

        ArrayNode leftField = objectMapper.createArrayNode();
        leftField.add("field");
        leftField.add(joinField2.getId());
        leftField.add(objectMapper.createObjectNode().put("base-type", "type/Integer"));
        conditionArray.add(leftField);

        ArrayNode rightField = objectMapper.createArrayNode();
        rightField.add("field");
        rightField.add(joinField1.getId());
        rightField.add(objectMapper.createObjectNode().put("base-type", "type/Integer").put("join-alias", addressTable.getName()));
        conditionArray.add(rightField);

        joinNode.set("condition", conditionArray);
        joinsArray.add(joinNode);
        queryNode.set("joins", joinsArray);

        return this;
    }


    public MetabaseQuery build() {
        return new MetabaseQuery(database.getId(), queryNode);
    }
}
