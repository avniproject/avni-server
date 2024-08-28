package org.avni.server.domain.metabase;

public enum BaseType {
    INTEGER("type/Integer"),
    TEXT("type/Text"),
    BOOLEAN("type/Boolean");

    private final String typeName;

    BaseType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
