package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.util.ObjectMapperSingleton;

// Refer docs : https://www.metabase.com/docs/latest/api/card#post-apicard
public class MetabaseRequestBody {
    private String name;
    private MetabaseQuery datasetQuery;
    private VisualizationType display;
    private String description;
    private ObjectNode visualizationSettings;
    private int collectionId;
    private Integer collectionPosition;
    private JsonNode resultMetadata;

    public MetabaseRequestBody(String name, MetabaseQuery datasetQuery, VisualizationType display, String description, ObjectNode visualizationSettings, int collectionId) {
        this.name = name;
        this.datasetQuery = datasetQuery;
        this.display = display;
        this.description = description;
        this.visualizationSettings = visualizationSettings;
        this.collectionId = collectionId;
    }

    public ObjectNode toJson() {
        ObjectNode rootNode = ObjectMapperSingleton.getObjectMapper().createObjectNode();
        rootNode.put("name", name);

        ObjectNode datasetQueryNode = ObjectMapperSingleton.getObjectMapper().createObjectNode();
        datasetQueryNode.put("database", datasetQuery.getDatabaseId());
        datasetQueryNode.put("type", "query");
        datasetQueryNode.set("query", datasetQuery.toJson());

        rootNode.set("dataset_query", datasetQueryNode);
        rootNode.put("display", display.toString());
        rootNode.putNull("description");
        rootNode.set("visualization_settings", visualizationSettings);
        rootNode.put("collection_id", collectionId);
        rootNode.putNull("collection_position");
        rootNode.putNull("result_metadata");

        return rootNode;
    }
}
