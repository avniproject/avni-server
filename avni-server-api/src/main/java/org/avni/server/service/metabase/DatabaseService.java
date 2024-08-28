package org.avni.server.service.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.avni.server.util.S;

@Service
public class DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;
    private final MetabaseService metabaseService;
    private Integer databaseId;
    private Integer collectionId;

    @Value("${metabase.api.url}")
    private String metabaseApiUrl;

    @Value("${metabase.api.key}")
    private String apiKey;

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, ObjectMapper objectMapper, MetabaseService metabaseService) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
        this.metabaseService = metabaseService;
    }

    private int getDatabaseId() {
        if (databaseId == null) {
            databaseId = metabaseService.getGlobalDatabaseId();
        }
        return databaseId;
    }

    private int getCollectionId() {
        if (collectionId == null) {
            collectionId = metabaseService.getGlobalCollectionId();
        }
        return collectionId;
    }

    public int getTableIdByName(String tableName) {
        JsonNode rootNode = databaseRepository.getDatabaseDetails(getDatabaseId());
        JsonNode tablesArray = rootNode.path("tables");
        for (JsonNode tableNode : tablesArray) {
            if (tableName.equals(tableNode.path("display_name").asText())) {
                return tableNode.path("id").asInt();
            }
        }
        return -1;
    }

    public int getTableIdByName(String tableName, String schema) {
        JsonNode rootNode = databaseRepository.getDatabaseDetails(
            getDatabaseId()
        );
        JsonNode tablesArray = rootNode.path("tables");

        for (JsonNode tableNode : tablesArray) {
            String tableSchema = tableNode.path("schema").asText();

            boolean schemaMatches = schema.equals("public")
                ? "public".equals(tableSchema)
                : !"public".equals(tableSchema);

            if (
                tableName.equals(tableNode.path("display_name").asText()) &&
                schemaMatches
            ) {
                return tableNode.path("id").asInt();
            }
        }
        return -1;
    }

    private String createRequestBodyForDataset(int sourceTableId) {
        return "{\"database\":" + getDatabaseId() + ",\"query\":{\"source-table\":" + sourceTableId + "},\"type\":\"query\",\"parameters\":[]}";
    }

    public int getFieldIdByTableNameAndFieldName(String tableName, String fieldName) {
        JsonNode fieldsArray = databaseRepository.getFields(getDatabaseId());
        String snakeCaseTableName = S.toSnakeCase(tableName);
        for (JsonNode fieldNode : fieldsArray) {
            if (snakeCaseTableName.equals(fieldNode.path("table_name").asText()) && fieldName.equals(fieldNode.path("name").asText())) {
                return fieldNode.path("id").asInt();
            }
        }
        return -1;
    }

    public SyncStatus getInitialSyncStatus() {
        JsonNode responseBody = databaseRepository.getInitialSyncStatus(getDatabaseId());
        String status = responseBody.path("initial_sync_status").asText();
        return SyncStatus.fromString(status);
    }

    private JsonNode getTableMetadata() {
        int tableMetadataId = getTableIdByName("Table Metadata");
        String requestBody = createRequestBodyForDataset(tableMetadataId);
        return databaseRepository.getDataset(requestBody);
    }

    public List<String> getSubjectTypeNames() {
        JsonNode tableMetadata = getTableMetadata();
        List<String> subjectTypeNames = new ArrayList<>();

        JsonNode rows = tableMetadata.path("data").path("rows");
        for (JsonNode row : rows) {
            String type = row.get(2).asText();
            if (Arrays.asList(TableType.INDIVIDUAL.getTypeName(), TableType.HOUSEHOLD.getTypeName(), TableType.GROUP.getTypeName(), TableType.PERSON.getTypeName()).contains(type)) {
                String rawName = row.get(1).asText();
                subjectTypeNames.add(S.formatName(rawName));
            }
        }
        System.out.println("The subject type names::" + subjectTypeNames);
        return subjectTypeNames;
    }


    public List<String> getProgramAndEncounterNames() {
        JsonNode tableMetadata = getTableMetadata();
        List<String> programNames = new ArrayList<>();

        JsonNode rows = tableMetadata.path("data").path("rows");
        for (JsonNode row : rows) {
            String type = row.get(2).asText();
            if (Arrays.asList(TableType.PROGRAM_ENCOUNTER.getTypeName(), TableType.PROGRAM_ENROLMENT.getTypeName()).contains(type)) {
                String rawName = row.get(1).asText();
                programNames.add(S.formatName(rawName));
            }
        }
        System.out.println("The program and encounter::" + programNames);
        return programNames;
    }


    private List<String> extractTableNames(JsonNode databaseDetails) {
        List<String> tableNames = new ArrayList<>();
        JsonNode tablesArray = databaseDetails.path("tables");
        for (JsonNode tableNode : tablesArray) {
            tableNames.add(tableNode.path("display_name").asText());
        }
        return tableNames;
    }

    private void createQuestionForTable(String tableName, String addressTableName, String addressField, String tableField) throws Exception {
        int addressTableId = getTableIdByName(addressTableName);
        int joinFieldId1 = getFieldIdByTableNameAndFieldName(addressTableName, addressField);
        int tableId = getTableIdByName(tableName);
        int joinFieldId2 = getFieldIdByTableNameAndFieldName(tableName, tableField);

        MetabaseJoin join = new MetabaseJoin("all", tableName, tableId, joinFieldId1, joinFieldId2, tableName, objectMapper);

        ArrayNode joinsArray = objectMapper.createArrayNode();
        joinsArray.add(join.toJson(objectMapper));

        MetabaseQuery query = new MetabaseQuery(getDatabaseId(),addressTableId, joinsArray);

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                "Address + " + tableName, query, VisualizationType.TABLE, null, objectMapper.createObjectNode(), getCollectionId(), CardType.QUESTION);

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
    }

    private void createQuestionForTable(String tableName, String schema) {
        int tableId = getTableIdByName(tableName, schema);

        ObjectNode datasetQuery = objectMapper.createObjectNode();
        datasetQuery.put("database", getDatabaseId());
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
        body.put("collection_id", getCollectionId());
        body.putNull("collection_position");
        body.putNull("result_metadata");

        databaseRepository.postForObject(
            metabaseApiUrl + "/card",
            body,
            JsonNode.class
        );
    }

    public void createQuestionsForSubjectTypes() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database initial sync is not complete.");
        }

        List<String> subjectTypeNames = getSubjectTypeNames();

        for (String subjectTypeName : subjectTypeNames) {
            createQuestionForTable(subjectTypeName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForProgramsAndEncounters() throws Exception{
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database initial sync is not complete.");
        }

        List<String> programNames = getProgramAndEncounterNames();

        JsonNode databaseDetails = databaseRepository.getDatabaseDetails(getDatabaseId());
        List<String> allTableNames = extractTableNames(databaseDetails);

        for (String programName : programNames) {
            for (String tableName : allTableNames) {
                if (tableName.equalsIgnoreCase(programName)) {
                    createQuestionForTable(tableName, "Address", "id", "address_id");
                }
            }
        }
    }

    public void createQuestionsForIndivdualTables() {
        List<String> tablesToCreateQuestionsFor = Arrays.asList(
            "Address",
            "Media",
            "Sync Telemetry"
        );

        for (String tableName : tablesToCreateQuestionsFor) {
            createQuestionForTable(tableName, "!public");
        }
    }
}