package org.avni.server.domain.metabase;

// Refer Documentation here : https://www.metabase.com/docs/latest/data-modeling/field-types

public enum FieldType {
    INTEGER("type/Integer"),
    TEXT("type/Text"),
    BOOLEAN("type/Boolean"),
    DATE("type/Date"),
    DATE_TIME_WITH_LOCAL_TZ("type/DateTimeWithLocalTZ");

    private final String typeName;

    FieldType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
