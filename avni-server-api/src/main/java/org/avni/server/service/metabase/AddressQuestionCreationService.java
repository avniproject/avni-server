package org.avni.server.service.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AddressQuestionCreationService implements QuestionCreationService {

    private final DatabaseService databaseService;
    private final DatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;
    private final String metabaseApiUrl;

    @Autowired
    public AddressQuestionCreationService(@Lazy DatabaseService databaseService, DatabaseRepository databaseRepository, ObjectMapper objectMapper, @Value("${metabase.api.url}") String metabaseApiUrl) {
        this.databaseService = databaseService;
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
        this.metabaseApiUrl = metabaseApiUrl;
    }

    @Override
    public void createQuestionForTable(String tableName, String addressTableName, String addressField, String tableField) throws Exception {
        int addressTableId = databaseService.getTableIdByName(addressTableName);
        int joinFieldId1 = databaseService.getFieldIdByTableNameAndFieldName(addressTableName, addressField);
        int tableId = databaseService.getTableIdByName(tableName);
        int joinFieldId2 = databaseService.getFieldIdByTableNameAndFieldName(tableName, tableField);

        MetabaseJoin join = new MetabaseJoin("all", tableName, tableId, joinFieldId1, joinFieldId2, tableName, objectMapper);

        ArrayNode joinsArray = objectMapper.createArrayNode();
        joinsArray.add(join.toJson(objectMapper));

        MetabaseQuery query = new MetabaseQuery(databaseService.getDatabaseId(), addressTableId, joinsArray);

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                "Address + " + tableName, query, VisualizationType.TABLE, null, objectMapper.createObjectNode(), databaseService.getCollectionId(), CardType.QUESTION);

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
    }

    @Override
    public void createQuestionForTable(String tableName, String schema) {
        int tableId = databaseService.getTableIdByName(tableName, schema);

        ObjectNode datasetQuery = objectMapper.createObjectNode();
        datasetQuery.put("database", databaseService.getDatabaseId());
        datasetQuery.put("type", "query");

        ObjectNode query = objectMapper.createObjectNode();
        query.put("source-table", tableId);
        datasetQuery.set("query", query);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", tableName);
        body.set("dataset_query", datasetQuery);
        body.put("display", "table");
        body.putNull("description");
        body.set("visualization_settings", objectMapper.createObjectNode());
        body.put("collection_id", databaseService.getCollectionId());
        body.putNull("collection_position");
        body.putNull("result_metadata");

        databaseRepository.postForObject(metabaseApiUrl + "/card", body, JsonNode.class);
    }
}
