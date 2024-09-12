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
        queryNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), tableDetails.getId());
        return this;
    }

    public MetabaseQueryBuilder joinWith(TableDetails addressTable, FieldDetails joinField1, FieldDetails joinField2) {
        ObjectNode joinNode = objectMapper.createObjectNode();
        joinNode.put(FieldAttribute.FIELDS.getAttributeName(), FieldAttribute.ALL.getAttributeName());
        joinNode.put(FieldAttribute.ALIAS.getAttributeName(), addressTable.getName());

        ArrayNode conditionArray = objectMapper.createArrayNode();
        conditionArray.add(ConditionType.EQUAL.getOperator());

        ArrayNode leftField = objectMapper.createArrayNode();
        leftField.add(FieldAttribute.FIELD.getAttributeName());
        leftField.add(joinField2.getId());
        leftField.add(objectMapper.createObjectNode().put(FieldAttribute.BASE_TYPE.getAttributeName(), FieldType.INTEGER.getTypeName()));
        conditionArray.add(leftField);

        ArrayNode rightField = objectMapper.createArrayNode();
        rightField.add(FieldAttribute.FIELD.getAttributeName());
        rightField.add(joinField1.getId());
        rightField.add(objectMapper.createObjectNode().put(FieldAttribute.BASE_TYPE.getAttributeName(), FieldType.INTEGER.getTypeName()).put(FieldAttribute.JOIN_ALIAS.getAttributeName(), addressTable.getName()));
        conditionArray.add(rightField);

        joinNode.set(FieldAttribute.CONDITION.getAttributeName(), conditionArray);
        joinNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), addressTable.getId());
        joinsArray.add(joinNode);
        queryNode.set(FieldAttribute.JOINS.getAttributeName(), joinsArray);

        return this;
    }


    public MetabaseQuery build() {
        return new MetabaseQuery(database.getId(), queryNode);
    }
}
