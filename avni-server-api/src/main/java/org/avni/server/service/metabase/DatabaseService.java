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
import java.util.Optional;
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

    public Database getGlobalDatabase() {
        int databaseId = metabaseService.getGlobalDatabaseId();
        return databaseRepository.getDatabaseByName(String.valueOf(databaseId));
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

    public int getTableIdByDisplayName(String tableName) {
        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getGlobalDatabase());
        List<TableDetails> tables = databaseInfo.getTables();

        for (TableDetails table : tables) {
            if (tableName.equals(table.getDisplayName())) {
                return table.getId();
            }
        }
        return -1;
    }

    public int getTableIdByDisplayName(String tableName, String schema) {
        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getGlobalDatabase());
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

    public TableDetails getTableDetailsByDisplayName(String tableName) {
        MetabaseDatabaseInfo databaseInfo = databaseRepository.getDatabaseDetails(getGlobalDatabase());
        List<TableDetails> tables = databaseInfo.getTables();

        // Handle Optional properly
        Optional<TableDetails> tableDetailsOptional = tables.stream()
                .filter(tableDetail -> tableDetail.nameMatches(tableName))
                .findFirst();

        return tableDetailsOptional.orElseThrow(() ->
                new RuntimeException("Table not found: " + tableName));
    }


    private String createRequestBodyForDataset(int sourceTableId) {
        return "{\"database\":" + getDatabaseId() + ",\"query\":{\"source-table\":" + sourceTableId + "},\"type\":\"query\",\"parameters\":[]}";
    }

    public int getFieldIdByTableNameAndFieldName(String tableName, String fieldName) {
        List<FieldDetails> fieldsList = databaseRepository.getFields(getGlobalDatabase());
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


    private DatasetResponse getTableMetadata(Database database) {
        TableDetails metadataTable = databaseRepository.getTableDetailsByDisplayName(database, "table_metadata");
        return databaseRepository.findAll(metadataTable, database);
    }

    public List<String> getSubjectTypeNames() {
        Database database = getGlobalDatabase();
        TableDetails metadataTable = databaseRepository.getTableDetailsByDisplayName(database, "table_metadata");

        DatasetResponse datasetResponse = databaseRepository.findAll(metadataTable, database);
        List<List<String>> rows = datasetResponse.getData().getRows();

        List<String> subjectTypeNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(2);
            if (type.equalsIgnoreCase(TableType.INDIVIDUAL.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.HOUSEHOLD.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.GROUP.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.PERSON.getTypeName())) {
                subjectTypeNames.add(row.get(1));
            }
        }

        return subjectTypeNames;
    }



    public List<String> getProgramAndEncounterNames() {
        Database database = getGlobalDatabase();
        TableDetails metadataTable = databaseRepository.getTableDetailsByDisplayName(database, "table_metadata");

        DatasetResponse datasetResponse = databaseRepository.findAll(metadataTable, database);
        List<List<String>> rows = datasetResponse.getData().getRows();

        List<String> programNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(2);
            if (type.equalsIgnoreCase(TableType.PROGRAM_ENCOUNTER.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.PROGRAM_ENROLMENT.getTypeName())) {
                programNames.add(row.get(1));
            }
        }

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

    private void createQuestionForTable(String tableName, String schema) {
        int tableId = getTableIdByDisplayName(tableName, schema);

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
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }

        Database database = getGlobalDatabase();
        List<String> subjectTypeNames = getSubjectTypeNames();

        for (String subjectTypeName : subjectTypeNames) {
            TableDetails subjectTable = databaseRepository.getTableDetailsByDisplayName(database, subjectTypeName);

            addressQuestionCreationService.createQuestionForTable(subjectTypeName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForProgramsAndEncounters() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }

        Database database = getGlobalDatabase();

        List<String> programAndEncounterNames = getProgramAndEncounterNames();

        for (String programName : programAndEncounterNames) {
            TableDetails programTable = databaseRepository.getTableDetailsByDisplayName(database, programName);
            addressQuestionCreationService.createQuestionForTable(programName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForIndividualTables() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }

        Database database = getGlobalDatabase();
        List<String> tablesToCreateQuestionsFor = Arrays.asList("Address", "Media", "Sync Telemetry");

        for (String tableName : tablesToCreateQuestionsFor) {
            TableDetails tableDetails = databaseRepository.getTableDetailsByDisplayName(database, tableName);
            databaseRepository.createQuestionForTable(database, tableDetails, "Address", "id", "address_id");
        }
    }

    public void createQuestions() throws Exception {
        createQuestionsForSubjectTypes();

        createQuestionsForProgramsAndEncounters();

        createQuestionsForIndividualTables();
    }
}
