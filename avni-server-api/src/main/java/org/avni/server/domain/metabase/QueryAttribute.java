package org.avni.server.domain.metabase;

public enum QueryAttribute {
    AGGREGATION("aggregation"),
    BREAKOUT("breakout"),
    FILTER("filter");

    private final String value;

    QueryAttribute(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
