package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

public class QuestionConfig {
    private AggregationType aggregationType;
    private String breakoutField;
    private FilterCondition[] filters;
    private VisualizationType visualizationType;
    private VisualizationAttributes[] visualizationAttributes;

    public QuestionConfig withAggregation(AggregationType aggregationType) {
        this.aggregationType = aggregationType;
        return this;
    }

    public QuestionConfig withBreakout(String breakoutField) {
        this.breakoutField = breakoutField;
        return this;
    }

    public QuestionConfig withFilters(FilterCondition... filters) {
        this.filters = filters;
        return this;
    }

    public QuestionConfig withVisualization(VisualizationType visualizationType, VisualizationAttributes... visualizationAttributes) {
        this.visualizationType = visualizationType;
        this.visualizationAttributes = visualizationAttributes;
        return this;
    }

    public ObjectNode withVisualizationSettings(String dimension, String metric) {
        ObjectNode settingsNode = ObjectMapperSingleton.getObjectMapper().createObjectNode();

        settingsNode.putArray("graph.dimensions").add(dimension);
        settingsNode.putArray("graph.metrics").add(metric);

        return settingsNode;
    }



    public AggregationType getAggregationType() {
        return aggregationType;
    }

    public String getBreakoutField() {
        return breakoutField;
    }

    public FilterCondition[] getFilters() {
        return filters;
    }

    public VisualizationType getVisualizationType() {
        return visualizationType;
    }

    public VisualizationAttributes[] getVisualizationSettings() {
        return visualizationAttributes;
    }

}
