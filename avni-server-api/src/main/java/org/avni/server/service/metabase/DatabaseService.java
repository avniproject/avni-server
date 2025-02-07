package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.MetabaseDashboardRepository;
import org.avni.server.dao.metabase.QuestionRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatabaseService implements QuestionCreationService {

    private static final String ADDRESS_TABLE = "Address";

    private final DatabaseRepository databaseRepository;
    private final MetabaseService metabaseService;
    private final CollectionRepository collectionRepository;
    private final QuestionRepository questionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, MetabaseService metabaseService, CollectionRepository collectionRepository, QuestionRepository questionRepository, MetabaseDashboardRepository metabaseDashboardRepository) {
        this.databaseRepository = databaseRepository;
        this.metabaseService = metabaseService;
        this.collectionRepository = collectionRepository;
        this.questionRepository = questionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
    }

    private Database getGlobalDatabase() {
        return metabaseService.getGlobalDatabase();
    }

    private CollectionInfoResponse getGlobalCollection() {
        return metabaseService.getGlobalCollection();
    }

    private CollectionItem getGlobalDashboard() {
        return metabaseService.getGlobalDashboard();
    }

    public Group getGlobalMetabaseGroup() {
        return metabaseService.getGlobalMetabaseGroup();
    }

    public SyncStatus getInitialSyncStatus() {
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(getGlobalDatabase());
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    private int getFieldId(TableDetails tableDetails, FieldDetails fieldDetails) {
        return databaseRepository.getFieldDetailsByName(getGlobalDatabase(), tableDetails, fieldDetails).getId();
    }

    private void ensureSyncComplete() {
        SyncStatus syncStatus = getInitialSyncStatus();
        if (syncStatus != SyncStatus.COMPLETE) {
            throw new RuntimeException("Database sync is not complete. Cannot create questions.");
        }
    }

    private List<String> filterOutExistingQuestions(List<String> entityNames) {
        Set<String> existingItemNames = collectionRepository.getExistingCollectionItems(getGlobalCollection().getIdAsInt()).stream()
                .map(item -> item.getName().trim().toLowerCase().replace(" ", "_"))
                .collect(Collectors.toSet());

        return entityNames.stream()
                .filter(entityName -> !existingItemNames.contains(entityName.toLowerCase()))
                .collect(Collectors.toList());
    }

    private boolean isQuestionMissing(String questionName) {
        Set<String> existingItemNames = collectionRepository
                .getExistingCollectionItems(getGlobalCollection().getIdAsInt())
                .stream()
                .map(item -> item.getName().trim().toLowerCase().replace(" ", "_"))
                .collect(Collectors.toSet());

        return !existingItemNames.contains(questionName.trim().toLowerCase().replace(" ", "_"));
    }

    private int getCardIdByQuestionName(String questionName) {
        return collectionRepository.getExistingCollectionItems(getGlobalCollection().getIdAsInt()).stream()
                .filter(item -> item.getName().trim().equalsIgnoreCase(questionName.trim()))
                .map(CollectionItem::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionName));
    }


    @Override
    public void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) {
        Database database = getGlobalDatabase();
        questionRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails);
    }

    @Override
    public void createQuestionForTable(String tableName) {
        Database database = getGlobalDatabase();
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(tableName, database.getName()));
        questionRepository.createQuestionForASingleTable(database, fetchedTableDetails);
    }

    private List<String> getSubjectTypeNames() {
        List<List<String>> rows = getTableMetadataRows();
        List<String> subjectTypeNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            String schemaName = row.get(DatasetColumn.SCHEMA_NAME.getIndex());
            if (schemaName.equalsIgnoreCase(getGlobalDatabase().getName()) &&
                    (type.equalsIgnoreCase(TableType.INDIVIDUAL.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.HOUSEHOLD.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.GROUP.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.PERSON.getTypeName()))) {
                subjectTypeNames.add(row.get(DatasetColumn.NAME.getIndex()));
            }
        }

        return subjectTypeNames;
    }

    private List<String> getProgramAndEncounterNames() {
        List<List<String>> rows = getTableMetadataRows();
        List<String> programAndEncounterNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            String schemaName = row.get(DatasetColumn.SCHEMA_NAME.getIndex());
            if (schemaName.equalsIgnoreCase(getGlobalDatabase().getName()) &&
                    (type.equalsIgnoreCase(TableType.PROGRAM_ENCOUNTER.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.ENCOUNTER.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.PROGRAM_ENROLMENT.getTypeName()))) {
                programAndEncounterNames.add(row.get(DatasetColumn.NAME.getIndex()));
            }
        }

        return programAndEncounterNames;
    }

    private List<List<String>> getTableMetadataRows() {
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails("table_metadata"));
        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
        List<List<String>> rows = datasetResponse.getData().getRows();
        return rows;
    }

    private void createQuestionsForEntities(List<String> entityNames, FieldDetails addressFieldDetails, FieldDetails entityFieldDetails) {
        ensureSyncComplete();
        Database database = getGlobalDatabase();
        TableDetails fetchedAddressTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(ADDRESS_TABLE, database.getName()));
        List<String> filteredEntities = filterOutExistingQuestions(entityNames);

        for (String entityName : filteredEntities) {
            TableDetails fetchedEntityTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(entityName, database.getName()));
            createQuestionForTable(fetchedEntityTableDetails, fetchedAddressTableDetails, addressFieldDetails, entityFieldDetails);
        }
    }

    private void createQuestionsForSubjectTypes() {
        List<String> subjectTypeNames = getSubjectTypeNames();
        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails subjectFieldDetails = new FieldDetails("address_id");
        createQuestionsForEntities(subjectTypeNames, addressFieldDetails, subjectFieldDetails);
    }

    private void createQuestionsForProgramsAndEncounters() {
        List<String> programAndEncounterNames = getProgramAndEncounterNames();
        FieldDetails addressFieldDetails = new FieldDetails("id");
        FieldDetails programOrEncounterFieldDetails = new FieldDetails("address_id");
        createQuestionsForEntities(programAndEncounterNames, addressFieldDetails, programOrEncounterFieldDetails);
    }

    private void createQuestionsForMiscSingleTables() {
        ensureSyncComplete();
        List<String> individualTables = Arrays.asList("address", "media", "sync_telemetry");
        List<String> filteredTables = filterOutExistingQuestions(individualTables);

        for (String tableName : filteredTables) {
            createQuestionForTable(tableName);
        }
    }

    private void createCustomQuestions() {
        ensureSyncComplete();
        Database database = getGlobalDatabase();
        if (isQuestionMissing(QuestionName.NonVoidedIndividual.getQuestionName())) {
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonVoidedIndividual, VisualizationType.PIE, Collections.EMPTY_LIST);
        }
        if (isQuestionMissing(QuestionName.NonExitedNonVoidedProgram.getQuestionName())) {
            FilterCondition additionalFilterCondition = new FilterCondition(ConditionType.IS_NULL,
                    databaseRepository.getFieldDetailsByName(database, new TableDetails(QuestionName.NonExitedNonVoidedProgram.getPrimaryTableName()),
                            new FieldDetails("program_exit_date_time")).getId(), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(), null);
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonExitedNonVoidedProgram, VisualizationType.PIE, Arrays.asList(additionalFilterCondition));
        }
        updateGlobalDashboardWithCustomQuestions();
    }

    public void updateGlobalDashboardWithCustomQuestions() {
        List<Dashcard> dashcards = new ArrayList<>();
        dashcards.add(new Dashcard(-1, getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), null, 0, 0, 12, 8));
        dashcards.add(new Dashcard(-2, getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), null, 0, 12, 12, 8));

        metabaseDashboardRepository.updateDashboard(getGlobalDashboard().getId(), new DashboardUpdateRequest(dashcards));
        addFilterToDashboard();

    }

    private void addFilterToDashboard() {
        List<Dashcard> updateDashcards = new ArrayList<>();
        updateDashcards.add(new Dashcard(-1, getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), null, 0, 0, 12, 8, Collections.emptyMap(), createDashcardParameterMappingForFirstDashcard()));
        updateDashcards.add(new Dashcard(-2, getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), null, 0, 12, 12, 8, Collections.emptyMap(), createDashcardParameterMappingForSecondDashcard()));
        metabaseDashboardRepository.updateDashboard(getGlobalDashboard().getId(), new DashboardUpdateRequest(updateDashcards, createParametersForDashboard()));

    }

    private List<ParameterMapping> createDashcardParameterMappingForFirstDashcard(){
        List<ParameterMapping> firstDashcardParameterMapping = new ArrayList<>();
        firstDashcardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(new TableDetails(TableName.INDIVIDUAL.getName()), new FieldDetails(FieldName.REGISTRATION_DATE.getName())), FieldType.DATE.getTypeName()))));
        return firstDashcardParameterMapping;
    }

    private List<ParameterMapping> createDashcardParameterMappingForSecondDashcard(){
        List<ParameterMapping> secondDashcardParameterMapping = new ArrayList<>();
        secondDashcardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(new TableDetails(TableName.PROGRAM_ENROLMENT.getName()), new FieldDetails(FieldName.ENROLMENT_DATE_TIME.getName())), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        return secondDashcardParameterMapping;
    }

    private List<Parameters> createParametersForDashboard(){
        List<Parameters> parameters = new ArrayList<>();
        parameters.add(new Parameters("Date Range","all_options","dateTimeId","date/all-options","date"));
        return parameters;
    }

    public void addCollectionItems() {
        createQuestionsForSubjectTypes();
        createQuestionsForProgramsAndEncounters();
        createQuestionsForMiscSingleTables();
        createCustomQuestions();
    }
}
