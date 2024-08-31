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
import java.util.stream.Collectors;

import org.avni.server.util.S;

@Service
public class DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;
    private final MetabaseService metabaseService;
    private final AddressQuestionCreationService addressQuestionCreationService;
    private Integer databaseId;
    private Integer collectionId;

    @Value("${metabase.api.url}")
    private String metabaseApiUrl;

    @Value("${metabase.api.key}")
    private String apiKey;

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, ObjectMapper objectMapper, MetabaseService metabaseService, AddressQuestionCreationService addressQuestionCreationService) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
        this.metabaseService = metabaseService;
        this.addressQuestionCreationService = addressQuestionCreationService;
    }

    public int getDatabaseId() {
        if (databaseId == null) {
            databaseId = metabaseService.getGlobalDatabaseId();
        }
        return databaseId;
    }

    public int getCollectionId() {
        if (collectionId == null) {
            collectionId = metabaseService.getGlobalCollectionId();
        }
        return collectionId;
    }

    public int getTableIdByName(String tableName) {
        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getDatabaseId());
        List<TableDetails> tables = databaseInfo.getTables();

        for (TableDetails table : tables) {
            if (tableName.equals(table.getDisplayName())) {
                return table.getId();
            }
        }
        return -1;
    }

    public int getTableIdByName(String tableName, String schema) {
        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getDatabaseId());
        List<TableDetails> tables = databaseInfo.getTables();

        for (TableDetails table : tables) {
            String tableSchema = table.getSchema();

            boolean schemaMatches = schema.equals("public")
                    ? "public".equals(tableSchema)
                    : !"public".equals(tableSchema);

            if (tableName.equals(table.getDisplayName()) && schemaMatches) {
                return table.getId();
            }
        }
        return -1;
    }


    private String createRequestBodyForDataset(int sourceTableId) {
        return "{\"database\":" + getDatabaseId() + ",\"query\":{\"source-table\":" + sourceTableId + "},\"type\":\"query\",\"parameters\":[]}";
    }

    public int getFieldIdByTableNameAndFieldName(String tableName, String fieldName) {
        List<FieldDetails> fieldsList = databaseRepository.getFields(getDatabaseId());
        String snakeCaseTableName = S.toSnakeCase(tableName);

        for (FieldDetails field : fieldsList) {
            if (snakeCaseTableName.equals(field.getTableName()) && fieldName.equals(field.getName())) {
                return field.getId();
            }
        }
        return -1;
    }

    public SyncStatus getInitialSyncStatus() {
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(getDatabaseId());
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }


    private DatasetResponse getTableMetadata() {
        int tableMetadataId = getTableIdByName("Table Metadata");
        String requestBody = createRequestBodyForDataset(tableMetadataId);
        return databaseRepository.getDataset(requestBody);
    }

    public List<String> getSubjectTypeNames() {
        DatasetResponse tableMetadata = getTableMetadata();
        List<String> subjectTypeNames = new ArrayList<>();

        List<List<String>> rows = tableMetadata.getData().getRows();
        for (List<String> row : rows) {
            String type = row.get(2);
            if (Arrays.asList(
                    TableType.INDIVIDUAL.getTypeName(),
                    TableType.HOUSEHOLD.getTypeName(),
                    TableType.GROUP.getTypeName(),
                    TableType.PERSON.getTypeName()
            ).contains(type)) {
                String rawName = row.get(1);
                subjectTypeNames.add(S.formatName(rawName));
            }
        }

        System.out.println("The subject type names: " + subjectTypeNames);
        return subjectTypeNames;
    }



    public List<String> getProgramAndEncounterNames() {
        DatasetResponse tableMetadata = getTableMetadata();
        List<String> programNames = new ArrayList<>();

        List<List<String>> rows = tableMetadata.getData().getRows();
        for (List<String> row : rows) {
            String type = row.get(2);
            if (Arrays.asList(
                    TableType.PROGRAM_ENCOUNTER.getTypeName(),
                    TableType.PROGRAM_ENROLMENT.getTypeName()
            ).contains(type)) {
                String rawName = row.get(1);
                programNames.add(S.formatName(rawName));
            }
        }

        System.out.println("The program and encounter names: " + programNames);
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
            addressQuestionCreationService.createQuestionForTable(subjectTypeName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForProgramsAndEncounters() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database initial sync is not complete.");
        }

        List<String> programNames = getProgramAndEncounterNames();

        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getDatabaseId());
        List<String> allTableNames = databaseInfo.getTables()
                .stream()
                .map(TableDetails::getDisplayName)
                .collect(Collectors.toList());

        for (String programName : programNames) {
            for (String tableName : allTableNames) {
                if (tableName.equalsIgnoreCase(programName)) {
                    addressQuestionCreationService.createQuestionForTable(tableName, "Address", "id", "address_id");
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
