package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

import java.util.List;

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

    public MetabaseQueryBuilder forTable(TableDetails tableDetails, List<FieldDetails> primaryTableFields) {
        queryNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), tableDetails.getId());
        selectedFieldsToDisplay(primaryTableFields, queryNode, null);
        return this;
    }

    public MetabaseQueryBuilder joinWith(TableDetails joinTargetTable, FieldDetails joinField1, FieldDetails joinField2, List<FieldDetails> fieldsToShow) {
        ObjectNode joinNode = objectMapper.createObjectNode();
        selectedFieldsToDisplay(fieldsToShow, joinNode, joinTargetTable);
        joinNode.put(FieldAttribute.ALIAS.getAttributeName(), joinTargetTable.getName());

        ArrayNode conditionArray = objectMapper.createArrayNode();
        conditionArray.add(ConditionType.EQUAL.getOperator());

        ArrayNode leftField = objectMapper.createArrayNode();
        leftField.add(FieldAttribute.FIELD.getAttributeName());
        leftField.add(joinField2.getId());
        leftField.add(objectMapper.createObjectNode().put(FieldAttribute.BASE_TYPE.getAttributeName(), joinField2.getBaseType()));
        conditionArray.add(leftField);

        ArrayNode rightField = objectMapper.createArrayNode();
        rightField.add(FieldAttribute.FIELD.getAttributeName());
        rightField.add(joinField1.getId());
        rightField.add(objectMapper.createObjectNode().put(FieldAttribute.BASE_TYPE.getAttributeName(),joinField2.getBaseType())
                .put(FieldAttribute.JOIN_ALIAS.getAttributeName(), joinTargetTable.getName()));
        conditionArray.add(rightField);

        joinNode.set(FieldAttribute.CONDITION.getAttributeName(), conditionArray);
        joinNode.put(FieldAttribute.SOURCE_TABLE.getAttributeName(), joinTargetTable.getId());
        joinsArray.add(joinNode);
        queryNode.set(FieldAttribute.JOINS.getAttributeName(), joinsArray);

        return this;
    }

    private void selectedFieldsToDisplay(List<FieldDetails> fieldsToShow, ObjectNode joinNode, TableDetails joinTargetTable) {
        if(fieldsToShow != null && !fieldsToShow.isEmpty()) {
            ArrayNode selectedFields = objectMapper.createArrayNode();
            fieldsToShow.forEach(field -> {
                ArrayNode selectedField = objectMapper.createArrayNode();
                selectedField.add(FieldAttribute.FIELD.getAttributeName());
                selectedField.add(field.getId());
                ObjectNode joinAliasNode = objectMapper.createObjectNode()
                        .put(FieldAttribute.BASE_TYPE.getAttributeName(), field.getBaseType());
                if(joinTargetTable != null) {
                    joinAliasNode
                            .put(FieldAttribute.JOIN_ALIAS.getAttributeName(), joinTargetTable.getName());
                }
                selectedField.add(joinAliasNode);
                selectedFields.add(selectedField);
            });
            joinNode.set(FieldAttribute.FIELDS.getAttributeName(), selectedFields);
        } else{
            joinNode.put(FieldAttribute.FIELDS.getAttributeName(), FieldAttribute.ALL.getAttributeName());
        }
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
