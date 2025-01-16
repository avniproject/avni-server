package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

public class FieldTarget {
    private final int fieldId;
    private final String baseType;

    public FieldTarget(int fieldId, String baseType) {
        this.fieldId = fieldId;
        this.baseType = baseType;
    }

    public ArrayNode toJson() {
        ArrayNode fieldArray = getObjectMapper().createArrayNode();
        fieldArray.add(FieldAttribute.FIELD.getAttributeName());
        fieldArray.add(fieldId);

        ObjectNode fieldDetails = getObjectMapper().createObjectNode();
        fieldDetails.put(FieldAttribute.BASE_TYPE.getAttributeName(), baseType);

        fieldArray.add(fieldDetails);
        return fieldArray;
    }
}
