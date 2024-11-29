package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatabaseService implements QuestionCreationService{

    private final DatabaseRepository databaseRepository;
    private final MetabaseService metabaseService;

    private static final String ADDRESS_TABLE = "Address";

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, MetabaseService metabaseService) {
        this.databaseRepository = databaseRepository;
        this.metabaseService = metabaseService;
    }

    public Database getGlobalDatabase() {
        return metabaseService.getGlobalDatabase();
    }

    public CollectionInfoResponse getGlobalCollection() {
        return metabaseService.getGlobalCollection();
    }

    public SyncStatus getInitialSyncStatus() {
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(getGlobalDatabase());
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    private void ensureSyncComplete() {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }
    }

    private List<String> filterOutExistingQuestions(List<String> entityNames) {
        Set<String> existingItemNames = databaseRepository.getExistingCollectionItems(getGlobalCollection().getIdAsInt()).stream()
                .map(item -> item.getName().trim().toLowerCase().replace(" ", "_"))
                .collect(Collectors.toSet());

        return entityNames.stream()
                .filter(entityName -> !existingItemNames.contains(entityName.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) {
        Database database = getGlobalDatabase();
        databaseRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails);
    }

    @Override
    public void createQuestionForTable(String tableName) {
        Database database = getGlobalDatabase();

        TableDetails tableDetails = new TableDetails(tableName);
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, tableDetails);

        databaseRepository.createQuestionForASingleTable(database, fetchedTableDetails);
    }

    public List<String> getSubjectTypeNames() {
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails("table_metadata"));

        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
        List<List<String>> rows = datasetResponse.getData().getRows();

        List<String> subjectTypeNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            if (type.equalsIgnoreCase(TableType.INDIVIDUAL.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.HOUSEHOLD.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.GROUP.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.PERSON.getTypeName())) {
                subjectTypeNames.add(row.get(DatasetColumn.NAME.getIndex()));
            }
        }

        return subjectTypeNames;
    }

    public List<String> getProgramAndEncounterNames() {
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails("table_metadata"));

        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
        List<List<String>> rows = datasetResponse.getData().getRows();

        List<String> programNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            if (type.equalsIgnoreCase(TableType.PROGRAM_ENCOUNTER.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.ENCOUNTER.getTypeName()) ||
                    type.equalsIgnoreCase(TableType.PROGRAM_ENROLMENT.getTypeName())) {
                programNames.add(row.get(DatasetColumn.NAME.getIndex()));
            }
        }

        return programNames;
    }

    private void createQuestionsForEntities(List<String> entityNames, FieldDetails addressFieldDetails, FieldDetails entityFieldDetails) {
        ensureSyncComplete();
        TableDetails fetchedAddressTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails(ADDRESS_TABLE));

        List<String> filteredEntities = filterOutExistingQuestions(entityNames);

        for (String entityName : filteredEntities) {
            TableDetails entityTableDetails = new TableDetails(entityName);
            TableDetails fetchedEntityTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), entityTableDetails);
            createQuestionForTable(fetchedEntityTableDetails, fetchedAddressTableDetails, addressFieldDetails, entityFieldDetails);
        }
    }

    public void createQuestionsForSubjectTypes() {
        List<String> subjectTypeNames = getSubjectTypeNames();
        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails subjectFieldDetails = new FieldDetails("address_id");
        createQuestionsForEntities(subjectTypeNames, addressFieldDetails, subjectFieldDetails);
    }

    public void createQuestionsForProgramsAndEncounters() {
        List<String> programAndEncounterNames = getProgramAndEncounterNames();
        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails programOrEncounterFieldDetails = new FieldDetails("address_id");
        createQuestionsForEntities(programAndEncounterNames, addressFieldDetails, programOrEncounterFieldDetails);
    }

    public void createQuestionsForIndividualTables() {
        ensureSyncComplete();
        List<String> individualTables = Arrays.asList("address", "media", "sync_telemetry");

        List<String> filteredTables = filterOutExistingQuestions(individualTables); 

        for (String tableName : filteredTables) {
            createQuestionForTable(tableName);
        }
    }

    public void createQuestions() {
        createQuestionsForSubjectTypes();

        createQuestionsForProgramsAndEncounters();

        createQuestionsForIndividualTables();
    }
}
