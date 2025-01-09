package org.avni.server.domain.metabase;

//Refer documentation : https://www.metabase.com/docs/latest/questions/sharing/visualizing-results
public enum VisualizationType {
    TABLE("table"),
    CHART("chart"),
    BAR("bar"),
    PIE("pie");;

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
