package org.avni.server.service.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

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
        Database globalDatabase = metabaseService.getGlobalDatabase();
        return databaseRepository.getDatabaseById(globalDatabase);
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

    public SyncStatus getInitialSyncStatus() {
        Database globalDatabase = metabaseService.getGlobalDatabase();
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(globalDatabase);
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    public List<String> getSubjectTypeNames() {
        Database database = getGlobalDatabase();
        TableDetails metadataTable = databaseRepository.getTableDetailsByName(database, "table_metadata");

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
        TableDetails metadataTable = databaseRepository.getTableDetailsByName(database, "table_metadata");

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

    public void createQuestionsForSubjectTypes() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }
        List<String> subjectTypeNames = getSubjectTypeNames();

        for (String subjectTypeName : subjectTypeNames) {
            addressQuestionCreationService.createQuestionForTable(subjectTypeName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForProgramsAndEncounters() throws Exception {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }

        List<String> programAndEncounterNames = getProgramAndEncounterNames();

        for (String programName : programAndEncounterNames) {
            addressQuestionCreationService.createQuestionForTable(programName, "Address", "id", "address_id");
        }
    }

    public void createQuestionsForIndividualTables() {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }

        Database database = getGlobalDatabase();

        List<String> individualTables = Arrays.asList("address", "media", "sync_telemetry");  // Adjust these as needed

        for (String tableName : individualTables) {
            TableDetails tableDetails = databaseRepository.getTableDetailsByName(database, tableName);

            MetabaseQuery query = new MetabaseQueryBuilder(database, objectMapper.createArrayNode(), objectMapper)
                    .forTable(tableDetails)
                    .build();

            MetabaseRequestBody requestBody = new MetabaseRequestBody(
                    tableName,
                    query,
                    VisualizationType.TABLE,
                    null,
                    objectMapper.createObjectNode(),
                    databaseRepository.getCollectionByName(database.getName()).getIdAsInt(),
                    CardType.QUESTION
            );

            databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
        }
    }


    public void createQuestions() throws Exception {
        createQuestionsForSubjectTypes();

        createQuestionsForProgramsAndEncounters();

        createQuestionsForIndividualTables();
    }
}
