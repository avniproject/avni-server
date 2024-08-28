package org.avni.server.domain.metabase;

public enum VisualizationType {
    TABLE("table"),
    CHART("chart");

    private final String typeName;

    VisualizationType(String typeName) {
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
