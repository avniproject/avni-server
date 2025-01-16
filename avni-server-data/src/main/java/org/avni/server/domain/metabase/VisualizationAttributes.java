package org.avni.server.domain.metabase;

public enum VisualizationAttributes {
    GRAPH_DIMENSIONS("graph.dimensions"),
    GRAPH_METRICS("graph.metrics");

    private final String value;

    VisualizationAttributes(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
