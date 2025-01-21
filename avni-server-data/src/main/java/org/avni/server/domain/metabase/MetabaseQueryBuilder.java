package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

public class MetabaseQueryBuilder {
    private final Database database;
    private final ArrayNode joinsArray;
    private final ObjectMapper objectMapper;
    private ObjectNode queryNode;

    public MetabaseQueryBuilder(Database database, ArrayNode joinsArray) {
        this.database = database;
        this.joinsArray = joinsArray;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
        this.queryNode = objectMapper.createObjectNode();
    }

    public MetabaseQueryBuilder forTable(TableDetails tableDetails) {
        queryNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), tableDetails.getId());
        return this;
    }

    public MetabaseQueryBuilder joinWith(TableDetails joinTargetTable, FieldDetails joinField1, FieldDetails joinField2) {
        ObjectNode joinNode = objectMapper.createObjectNode();
        joinNode.put(FieldAttribute.FIELDS.getAttributeName(), FieldAttribute.ALL.getAttributeName());
        joinNode.put(FieldAttribute.ALIAS.getAttributeName(), joinTargetTable.getName());

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
        rightField.add(objectMapper.createObjectNode().put(FieldAttribute.BASE_TYPE.getAttributeName(), FieldType.INTEGER.getTypeName()).put(FieldAttribute.JOIN_ALIAS.getAttributeName(), joinTargetTable.getName()));
        conditionArray.add(rightField);

        joinNode.set(FieldAttribute.CONDITION.getAttributeName(), conditionArray);
        joinNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), joinTargetTable.getId());
        joinsArray.add(joinNode);
        queryNode.set(FieldAttribute.JOINS.getAttributeName(), joinsArray);

        return this;
    }

    public MetabaseQueryBuilder addAggregation(AggregationType aggregationType) {
        queryNode.putArray(QueryAttribute.AGGREGATION.getValue()).add(objectMapper.createArrayNode().add(aggregationType.getValue()));
        return this;
    }

    public MetabaseQueryBuilder addBreakout(int fieldId, String baseType, int sourceFieldId) {
        ArrayNode breakoutArray = objectMapper.createArrayNode();
        breakoutArray.add(FieldAttribute.FIELD.getAttributeName());
        breakoutArray.add(fieldId);

        ObjectNode fieldDetails = objectMapper.createObjectNode();
        fieldDetails.put(FieldAttribute.BASE_TYPE.getAttributeName(), baseType);
        fieldDetails.put(FieldAttribute.SOURCE_FIELD.getAttributeName(), sourceFieldId);

        breakoutArray.add(fieldDetails);
        queryNode.withArray(QueryAttribute.BREAKOUT.getValue()).add(breakoutArray);
        return this;
    }

    public MetabaseQueryBuilder addFilter(FilterCondition[] conditions) {
        ArrayNode filterArray = objectMapper.createArrayNode();
        filterArray.add("and");
        for (FilterCondition condition : conditions) {
            filterArray.add(condition.toJson());
        }
        queryNode.set(QueryAttribute.FILTER.getValue(), filterArray);
        return this;
    }


    public MetabaseQuery build() {
        return new MetabaseQuery(database.getId(), queryNode);
    }
}
