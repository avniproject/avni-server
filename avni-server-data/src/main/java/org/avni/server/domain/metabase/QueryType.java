package org.avni.server.domain.metabase;

public enum QueryType {
    QUERY("query"),
    DASHBOARD("dashboard");

    private final String typeName;

    QueryType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }
}
