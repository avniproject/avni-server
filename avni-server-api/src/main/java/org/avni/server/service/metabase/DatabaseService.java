package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final MetabaseService metabaseService;
    private final AddressQuestionCreationService addressQuestionCreationService;

    private static final String ADDRESS_TABLE = "Address";

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, MetabaseService metabaseService, AddressQuestionCreationService addressQuestionCreationService) {
        this.databaseRepository = databaseRepository;
        this.metabaseService = metabaseService;
        this.addressQuestionCreationService = addressQuestionCreationService;
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

    public List<String> getSubjectTypeNames() {
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails("table_metadata"));

        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
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
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails("table_metadata"));

        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
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

    public void createQuestionsForSubjectTypes(){
        ensureSyncComplete();
        List<String> subjectTypeNames = getSubjectTypeNames();

        TableDetails addressTableDetails = new TableDetails(ADDRESS_TABLE);
        TableDetails fetchedAddressTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), addressTableDetails);

        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails subjectFieldDetails = new FieldDetails("address_id");


        for (String subjectTypeName : subjectTypeNames) {
            TableDetails subjectTableDetails = new TableDetails();
            subjectTableDetails.setName(subjectTypeName);
            TableDetails fetchedSubjectTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), subjectTableDetails);
            addressQuestionCreationService.createQuestionForTable(fetchedSubjectTableDetails, fetchedAddressTableDetails, addressFieldDetails, subjectFieldDetails);
        }
    }

    public void createQuestionsForProgramsAndEncounters(){
        ensureSyncComplete();
        List<String> programAndEncounterNames = getProgramAndEncounterNames();

        TableDetails fetchedAddressTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails(ADDRESS_TABLE));

        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails programFieldDetails = new FieldDetails("address_id");

        for (String programName : programAndEncounterNames) {
            TableDetails programTableDetails = new TableDetails();
            programTableDetails.setName(programName);
            TableDetails fetchedProgramTableDetails = databaseRepository.findTableDetailsByName(getGlobalDatabase(), programTableDetails);
            addressQuestionCreationService.createQuestionForTable(fetchedProgramTableDetails, fetchedAddressTableDetails, addressFieldDetails, programFieldDetails);
        }
    }

    public void createQuestionsForIndividualTables(){
        ensureSyncComplete();

        List<String> individualTables = Arrays.asList("address", "media", "sync_telemetry");

        for (String tableName : individualTables) {
            addressQuestionCreationService.createQuestionForTable(tableName, "!public");
        }
    }

    public void createQuestions() {
        createQuestionsForSubjectTypes();

        createQuestionsForProgramsAndEncounters();

        createQuestionsForIndividualTables();
    }
}
