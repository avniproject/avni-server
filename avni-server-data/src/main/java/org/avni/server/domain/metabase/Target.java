package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

public class Target {
    private final MetabaseTargetType targetType;
    private final FieldTarget fieldTarget;

    public Target(MetabaseTargetType targetType, FieldTarget fieldTarget) {
        this.targetType = targetType;
        this.fieldTarget = fieldTarget;
    }

    public ArrayNode toJson() {
        ArrayNode targetArray = getObjectMapper().createArrayNode();
        targetArray.add(targetType.getValue());
        targetArray.add(fieldTarget.toJson());
        return targetArray;
    }
}
