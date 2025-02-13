package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

public class FieldTarget {
    private final int fieldId;
    private final String baseType;
    private String joinAlias;

    public FieldTarget(int fieldId, String baseType) {
        this.fieldId = fieldId;
        this.baseType = baseType;
    }

    public FieldTarget(int fieldId, String baseType, String joinAlias) {
        this.fieldId = fieldId;
        this.baseType = baseType;
        this.joinAlias = joinAlias;
    }

    public ArrayNode toJson() {
        ArrayNode fieldArray = getObjectMapper().createArrayNode();
        fieldArray.add(FieldAttribute.FIELD.getAttributeName());
        fieldArray.add(fieldId);

        ObjectNode fieldDetails = getObjectMapper().createObjectNode();
        fieldDetails.put(FieldAttribute.BASE_TYPE.getAttributeName(), baseType);
        if (joinAlias != null) {
            fieldDetails.put(FieldAttribute.JOIN_ALIAS.getAttributeName(), joinAlias);
        }

        fieldArray.add(fieldDetails);
        return fieldArray;
    }
}
