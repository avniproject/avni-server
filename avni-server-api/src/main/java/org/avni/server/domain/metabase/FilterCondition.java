package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

public class FilterCondition {
    private final ConditionType operator;
    private final Integer fieldId;
    private final String baseType;
    private final Object value;
    private Integer sourceFieldId;

    public FilterCondition(ConditionType operator, int fieldId, String baseType, Object value) {
        this.operator = operator;
        this.fieldId = fieldId;
        this.baseType = baseType;
        this.value = value;
    }

    public FilterCondition(ConditionType operator, int fieldId, String baseType, Object value,int sourceFieldId) {
        this.operator = operator;
        this.fieldId = fieldId;
        this.baseType = baseType;
        this.value = value;
        this.sourceFieldId=sourceFieldId;
    }

    public ConditionType getOperator() {
        return operator;
    }

    public int getFieldId() {
        return fieldId;
    }

    public String getBaseType() {
        return baseType;
    }

    public Object getValue() {
        return value;
    }

    public ArrayNode toJson() {
        ArrayNode filterArray = getObjectMapper().createArrayNode();
        filterArray.add(operator.getOperator());

        ArrayNode fieldArray = getObjectMapper().createArrayNode();
        fieldArray.add(FieldAttribute.FIELD.getAttributeName());
        fieldArray.add(fieldId);

        ObjectNode fieldDetails = getObjectMapper().createObjectNode();
        fieldDetails.put(FieldAttribute.BASE_TYPE.getAttributeName(), baseType);

        if(sourceFieldId!=null){
            fieldDetails.put(FieldAttribute.SOURCE_FIELD.getAttributeName(), sourceFieldId);
        }

        fieldArray.add(fieldDetails);
        filterArray.add(fieldArray);
        if(value!=null){
            if (value instanceof String[]) {
                for (String val : (String[]) value) {
                    filterArray.add(val);
                }
            } else {
                filterArray.addPOJO(value);
            }
        }
        return filterArray;

    }
}
