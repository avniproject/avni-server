package org.avni.server.domain.metabase;

public enum MetabaseTargetType {
    DIMENSION("dimension");

    private final String value;

    MetabaseTargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
