package org.avni.server.domain.metabase;

public enum AggregationType {
    COUNT("count"),
    SUM("sum"),
    AVG("avg"),
    MIN("min"),
    MAX("max");

    private final String value;

    AggregationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
