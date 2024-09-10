package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public enum FieldAttribute {
    FIELD("field"),
    FIELDS("fields"),
    SOURCE_TABLE("source-table"),
    ALIAS("alias"),
    CONDITION("condition"),
    JOINS("joins"),
    JOIN_ALIAS("join-alias"),
    BASE_TYPE("base-type");

    private final String attributeName;

    FieldAttribute(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public ObjectNode toJson(ObjectMapper objectMapper, Object value) {
        ObjectNode attributeNode = objectMapper.createObjectNode();
        attributeNode.put(attributeName, value.toString());
        return attributeNode;
    }
}
