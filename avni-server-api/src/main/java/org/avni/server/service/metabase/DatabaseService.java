package org.avni.server.service.metabase;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.MetabaseDashboardRepository;
import org.avni.server.dao.metabase.QuestionRepository;
import org.avni.server.domain.JoinTableConfig;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatabaseService implements IQuestionCreationService {

    private static final String INDIVIDUAL_TABLE = "individual";
    private static final String ENROLMENT_TABLE = "program_enrolment";
    private static final String ADDRESS_TABLE = "address";
    private static final String SUBJECT_TYPE_TABLE = "subject_type";
    private static final String PROGRAM_TABLE = "program";
    private static final String GENDER_TABLE = "gender";
    public static final String INDIVIDUAL_TYPE_GENDER_ADDRESS_TABLE = "individual_type_gender_address";
    public static final String ENROLMENT_TYPE_INDIVIDUAL_ADDRESS_TABLE = "enrolment_type_individual_address";
    public static final String TABLE_METADATA = "table_metadata";
    public static final String MEDIA_TABLE = "media";
    public static final String SYNC_TELEMETRY_TABLE = "sync_telemetry";
    private static final String PUBLIC_SCHEMA = "public";

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String UUID = "uuid";
    private static final String BLOCK = "Block";
    private static final String SUBJECT_TYPE_ID = "subject_type_id";
    public static final String FIRST_NAME = "first_name";
    public static final String MIDDLE_NAME = "middle_name";
    public static final String LAST_NAME = "last_name";
    public static final String DATE_OF_BIRTH = "date_of_birth";
    public static final String ADDRESS_ID = "address_id";
    public static final String REGISTRATION_DATE = "registration_date";
    public static final String CREATED_DATE_TIME = "created_date_time";
    public static final String ENROLMENT_DATE_TIME ="enrolment_date_time";
    public static final String LAST_MODIFIED_DATE_TIME = "last_modified_date_time";
    public static final String GENDER_ID = "gender_id";
    public static final String PROGRAM_ID = "program_id";
    public static final String INDIVIDUAL_ID = "individual_id";
    public static final String PROGRAM_EXIT_DATE_TIME = "program_exit_date_time";
    public static final String VILLAGE_HAMLET = "Village/Hamlet";
    public static final String PROJECT_BLOCK = "Project/Block";

    public static final List<String> PROG_ENROLMENT_TABLE_FIELDS = List.of(ID, UUID, ENROLMENT_DATE_TIME, CREATED_DATE_TIME, LAST_MODIFIED_DATE_TIME);
    public static final List<String> INDIVIDUAL_TABLE_FIELDS = List.of(ID, UUID, SUBJECT_TYPE_ID, FIRST_NAME, MIDDLE_NAME, LAST_NAME, DATE_OF_BIRTH, ADDRESS_ID, REGISTRATION_DATE, CREATED_DATE_TIME, LAST_MODIFIED_DATE_TIME);

    private final DatabaseRepository databaseRepository;
    private final MetabaseService metabaseService;
    private final CollectionRepository collectionRepository;
    private final QuestionRepository questionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    public DatabaseService(DatabaseRepository databaseRepository, MetabaseService metabaseService, CollectionRepository collectionRepository, QuestionRepository questionRepository, MetabaseDashboardRepository metabaseDashboardRepository, AddressLevelTypeRepository addressLevelTypeRepository) {
        this.databaseRepository = databaseRepository;
        this.metabaseService = metabaseService;
        this.collectionRepository = collectionRepository;
        this.questionRepository = questionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
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
        questionRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails, Collections.emptyList());
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
        TableDetails fetchedMetadataTable = databaseRepository.findTableDetailsByName(getGlobalDatabase(), new TableDetails(TABLE_METADATA));
        DatasetResponse datasetResponse = databaseRepository.findAll(fetchedMetadataTable, getGlobalDatabase());
        return datasetResponse.getData().getRows();
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
        FieldDetails addressFieldDetails = new FieldDetails(ID);
        FieldDetails subjectFieldDetails = new FieldDetails(ADDRESS_ID);
        createQuestionsForEntities(subjectTypeNames, addressFieldDetails, subjectFieldDetails);
    }

    private void createQuestionsForProgramsAndEncounters() {
        List<String> programAndEncounterNames = getProgramAndEncounterNames();
        FieldDetails addressFieldDetails = new FieldDetails(ID);
        FieldDetails programOrEncounterFieldDetails = new FieldDetails(ADDRESS_ID);
        createQuestionsForEntities(programAndEncounterNames, addressFieldDetails, programOrEncounterFieldDetails);
    }

    private void createQuestionsForMiscSingleTables() {
        ensureSyncComplete();
        List<String> miscSingleTableNames = Arrays.asList(ADDRESS_TABLE, MEDIA_TABLE, SYNC_TELEMETRY_TABLE);
        List<String> filteredTables = filterOutExistingQuestions(miscSingleTableNames);

        for (String tableName : filteredTables) {
            createQuestionForTable(tableName);
        }
    }

    private void createQuestionsForMiscJoinedTables() {
        ensureSyncComplete();
        Database database = getGlobalDatabase();
        List<String> miscJoinedTables = Arrays.asList(INDIVIDUAL_TYPE_GENDER_ADDRESS_TABLE, ENROLMENT_TYPE_INDIVIDUAL_ADDRESS_TABLE);
        List<String> filteredTables = filterOutExistingQuestions(miscJoinedTables);
        for (String tableName : filteredTables) {
            createQuestionForJoinedTable(database, tableName);
        }
    }

    private void createQuestionForJoinedTable(Database database, String tableName) {
        switch (tableName) {
            case INDIVIDUAL_TYPE_GENDER_ADDRESS_TABLE:
                createIndividualTypeGenderAddress(database, tableName);
                break;
            case ENROLMENT_TYPE_INDIVIDUAL_ADDRESS_TABLE:
                createEnrolmentTypeIndividualAddress(database, tableName);
                break;
            default:
                break;
        }
    }

    private void createIndividualTypeGenderAddress(Database database, String displayName) {
        TableDetails primaryTableDetails = new TableDetails(INDIVIDUAL_TABLE);
        TableDetails subjectTypeTableDetails = new TableDetails(SUBJECT_TYPE_TABLE);
        TableDetails genderTableDetails = new TableDetails(GENDER_TABLE);
        TableDetails addressTableDetails = new TableDetails(ADDRESS_TABLE, database.getName());
        TableDetails fetchedEntityTableDetails = databaseRepository.findTableDetailsByName(database, primaryTableDetails);
        fetchedEntityTableDetails.setDisplayName(displayName);
        fetchedEntityTableDetails.setDescription(displayName);

        List<FieldDetails> subjectTypeTableFieldsDetails = getSubjectTypeFields(database, subjectTypeTableDetails);

        List<FieldDetails> genderTableFieldsDetails = getGenderFields(database, genderTableDetails);

        List<FieldDetails> addressTableFieldsDetails = getAddressFields(database, addressTableDetails);

        List<JoinTableConfig> joinTableConfigs = getISGAJoinTableConfigs(database, subjectTypeTableFieldsDetails, genderTableFieldsDetails, addressTableFieldsDetails);

        List<FieldDetails> primaryTableFieldsDetails = getPrimaryTableFields(database, INDIVIDUAL_TABLE_FIELDS, primaryTableDetails);

        questionRepository.createQuestionForTableWithMultipleJoins(database, fetchedEntityTableDetails, joinTableConfigs, primaryTableFieldsDetails);
    }

    private List<JoinTableConfig> getISGAJoinTableConfigs(Database database, List<FieldDetails> subjectTypeTableFieldsDetails, List<FieldDetails> genderTableFieldsDetails, List<FieldDetails> addressTableFieldsDetails) {
        List<JoinTableConfig> joinTableConfigs = new ArrayList<>();
        joinTableConfigs.add(configureJoinTable(database, ID, SUBJECT_TYPE_ID, SUBJECT_TYPE_TABLE, PUBLIC_SCHEMA, subjectTypeTableFieldsDetails));
        joinTableConfigs.add(configureJoinTable(database, ID, GENDER_ID, GENDER_TABLE, PUBLIC_SCHEMA, genderTableFieldsDetails));
        joinTableConfigs.add(configureJoinTable(database, ID, ADDRESS_ID, ADDRESS_TABLE, database.getName(), addressTableFieldsDetails));
        return joinTableConfigs;
    }

    private List<FieldDetails> getAddressFields(Database database, TableDetails addressTableDetails) {
        List<FieldDetails> addressTableFieldsDetails = new ArrayList<>();
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        List<String> addressTableFields = new ArrayList<>(List.of(ID, UUID));
        addressTableFields.addAll(addressLevelTypeNames);
        for(String addressTableField : addressTableFields) {
            addressTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, addressTableDetails, new FieldDetails(addressTableField)));
        }
        return addressTableFieldsDetails;
    }

    private List<FieldDetails> getGenderFields(Database database, TableDetails genderTableDetails) {
        List<FieldDetails> genderTableFieldsDetails = new ArrayList<>();
        List<String> genderTableFields = List.of(ID,NAME);
        for(String genderTableField : genderTableFields) {
            genderTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, genderTableDetails, new FieldDetails(genderTableField)));
        }
        return genderTableFieldsDetails;
    }

    private List<FieldDetails> getSubjectTypeFields(Database database, TableDetails subjectTypeTableDetails) {
        List<FieldDetails> subjectTypeTableFieldsDetails = new ArrayList<>();
        List<String> subjectTypeTableFields = List.of(ID,NAME,UUID);
        for(String subjectTypeTableField : subjectTypeTableFields) {
            subjectTypeTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, subjectTypeTableDetails, new FieldDetails(subjectTypeTableField)));
        }
        return subjectTypeTableFieldsDetails;
    }

    private void createEnrolmentTypeIndividualAddress(Database database, String displayName) {
        TableDetails primaryTableDetails = new TableDetails(ENROLMENT_TABLE);
        TableDetails programTableDetails = new TableDetails(PROGRAM_TABLE);
        TableDetails individualTableDetails = new TableDetails(INDIVIDUAL_TABLE);
        TableDetails addressTableDetails = new TableDetails(ADDRESS_TABLE, database.getName());

        TableDetails fetchedEntityTableDetails = databaseRepository.findTableDetailsByName(database, primaryTableDetails);
        fetchedEntityTableDetails.setDisplayName(displayName);
        fetchedEntityTableDetails.setDescription(displayName);

        List<FieldDetails> programTableFieldsDetails = getProgramFields(database, programTableDetails);
        List<FieldDetails> individualTableFieldsDetails = getIndividualFields(database, individualTableDetails);
        List<FieldDetails> addressTableFieldsDetails = getAddressFields(database, addressTableDetails);
        List<JoinTableConfig> joinTableConfigs = getPEIAJoinTableConfigs(database, programTableFieldsDetails, individualTableFieldsDetails, addressTableFieldsDetails);

        List<FieldDetails> primaryTableFieldsDetails = getPrimaryTableFields(database, PROG_ENROLMENT_TABLE_FIELDS, primaryTableDetails);

        questionRepository.createQuestionForTableWithMultipleJoins(database, fetchedEntityTableDetails, joinTableConfigs, primaryTableFieldsDetails);
    }

    private List<FieldDetails> getPrimaryTableFields(Database database, List<String> primaryTableFields, TableDetails primaryTableDetails) {
        List<FieldDetails> primaryTableFieldsDetails = new ArrayList<>();
        for(String primaryTableField : primaryTableFields) {
            primaryTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, primaryTableDetails, new FieldDetails(primaryTableField)));
        }
        return primaryTableFieldsDetails;
    }

    private List<JoinTableConfig> getPEIAJoinTableConfigs(Database database, List<FieldDetails> programTableFieldsDetails, List<FieldDetails> individualTableFieldsDetails, List<FieldDetails> addressTableFieldsDetails) {
        List<JoinTableConfig> joinTableConfigs = new ArrayList<>();
        joinTableConfigs.add(configureJoinTable(database, ID, PROGRAM_ID, PROGRAM_TABLE, PUBLIC_SCHEMA, programTableFieldsDetails));
        joinTableConfigs.add(configureJoinTable(database, ID, INDIVIDUAL_ID, INDIVIDUAL_TABLE, PUBLIC_SCHEMA, individualTableFieldsDetails));
        joinTableConfigs.add(configureJoinTable(database, ID, ADDRESS_ID, ADDRESS_TABLE, database.getName(), addressTableFieldsDetails));
        return joinTableConfigs;
    }

    private List<FieldDetails> getIndividualFields(Database database, TableDetails individualTableDetails) {
        List<FieldDetails> individualTableFieldsDetails = new ArrayList<>();
        List<String> individualTableFields = List.of(FIRST_NAME,LAST_NAME);
        for(String individualTableField : individualTableFields) {
            individualTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, individualTableDetails, new FieldDetails(individualTableField)));
        }
        return individualTableFieldsDetails;
    }

    private List<FieldDetails> getProgramFields(Database database, TableDetails programTableDetails) {
        List<FieldDetails> programTableFieldsDetails = new ArrayList<>();
        List<String> programTableFields = List.of(NAME);
        for(String programTableField : programTableFields) {
            programTableFieldsDetails.add(databaseRepository.getFieldDetailsByName(database, programTableDetails, new FieldDetails(programTableField)));
        }
        return programTableFieldsDetails;
    }


    private JoinTableConfig configureJoinTable(Database database, String targetTableJoinColumn,
                                               String sourceTableJoinColumn, String targetTable, String targetTableSchemaName, List<FieldDetails> fieldsToShow) {
        FieldDetails fieldDetails = new FieldDetails(targetTableJoinColumn);
        FieldDetails entityFieldDetails = new FieldDetails(sourceTableJoinColumn);
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(targetTable, targetTableSchemaName));
        return new JoinTableConfig(fetchedTableDetails, fieldDetails, entityFieldDetails, fieldsToShow);
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
                            new FieldDetails(PROGRAM_EXIT_DATE_TIME)).getId(), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(), null);
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
        //todo add field details and table details to request scope
        createQuestionsForSubjectTypes();
        createQuestionsForProgramsAndEncounters();
        createQuestionsForMiscSingleTables();

        createQuestionsForMiscJoinedTables();

        createCustomQuestions();
    }
}
