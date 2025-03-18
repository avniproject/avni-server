package org.avni.server.service.metabase;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.MetabaseDatabaseRepository;
import org.avni.server.dao.metabase.MetabaseDashboardRepository;
import org.avni.server.dao.metabase.QuestionRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatabaseService implements IQuestionCreationService {

    private static final String ADDRESS_TABLE = "address";
    private static final String MEDIA_TABLE = "media";
    private static final String SYNC_TELEMETRY_TABLE = "sync_telemetry";

    private static final String ID = "id";
    private static final String ADDRESS_ID = "address_id";
    private static final String PROGRAM_EXIT_DATE_TIME = "program_exit_date_time";

    private static final int SECOND_CARD_COL_IDX = 12;
    private static final int FIRST_CARD_COL_IDX = 0;

    private final MetabaseDatabaseRepository databaseRepository;
    private final MetabaseService metabaseService;
    private final CollectionRepository collectionRepository;
    private final QuestionRepository questionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationService organisationService;

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final long MAX_WAIT_TIME_IN_SECONDS = 300;
    private static final long EACH_SLEEP_DURATION = 3;

    @Autowired
    public DatabaseService(MetabaseDatabaseRepository databaseRepository, MetabaseService metabaseService, CollectionRepository collectionRepository, QuestionRepository questionRepository, MetabaseDashboardRepository metabaseDashboardRepository, AddressLevelTypeRepository addressLevelTypeRepository, OrganisationService organisationService) {
        this.databaseRepository = databaseRepository;
        this.metabaseService = metabaseService;
        this.collectionRepository = collectionRepository;
        this.questionRepository = questionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationService = organisationService;
    }

    private CollectionInfoResponse getOrgCollection() {
        return metabaseService.getGlobalCollection();
    }

    public SyncStatus getInitialSyncStatus() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    private int getFieldId(TableDetails tableDetails, FieldDetails fieldDetails) {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        return databaseRepository.getFieldDetailsByName(database, tableDetails, fieldDetails).getId();
    }

    private List<String> filterOutExistingQuestions(List<String> entityNames) {
        Set<String> existingItemNames = collectionRepository.getExistingCollectionItems(getOrgCollection().getIdAsInt()).stream()
                .map(item -> item.getName().trim().toLowerCase().replace(" ", "_"))
                .collect(Collectors.toSet());

        return entityNames.stream()
                .filter(entityName -> !existingItemNames.contains(entityName.toLowerCase()))
                .collect(Collectors.toList());
    }

    private boolean isQuestionMissing(String questionName) {
        Set<String> existingItemNames = collectionRepository
                .getExistingCollectionItems(getOrgCollection().getIdAsInt())
                .stream()
                .map(item -> item.getName().trim().toLowerCase().replace(" ", "_"))
                .collect(Collectors.toSet());

        return !existingItemNames.contains(questionName.trim().toLowerCase().replace(" ", "_"));
    }

    private int getCardIdByQuestionName(String questionName) {
        return collectionRepository.getExistingCollectionItems(getOrgCollection().getIdAsInt()).stream()
                .filter(item -> item.getName().trim().equalsIgnoreCase(questionName.trim()))
                .map(CollectionItem::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionName));
    }


    @Override
    public void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        questionRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails, Collections.emptyList());
    }

    @Override
    public void createQuestionForTable(String tableName) {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(tableName, database.getName()));
        questionRepository.createQuestionForASingleTable(database, fetchedTableDetails);
    }

    private List<String> getSubjectTypeNames() {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        List<List<String>> rows = getTableMetadataRows();
        List<String> subjectTypeNames = new ArrayList<>();

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            String schemaName = row.get(DatasetColumn.SCHEMA_NAME.getIndex());
            if (schemaName.equalsIgnoreCase(database.getName()) &&
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
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());

        for (List<String> row : rows) {
            String type = row.get(DatasetColumn.TYPE.getIndex());
            String schemaName = row.get(DatasetColumn.SCHEMA_NAME.getIndex());
            if (schemaName.equalsIgnoreCase(database.getName()) &&
                    (type.equalsIgnoreCase(TableType.PROGRAM_ENCOUNTER.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.ENCOUNTER.getTypeName()) ||
                            type.equalsIgnoreCase(TableType.PROGRAM_ENROLMENT.getTypeName()))) {
                programAndEncounterNames.add(row.get(DatasetColumn.NAME.getIndex()));
            }
        }

        return programAndEncounterNames;
    }

    private void createQuestionsForEntities(List<String> entityNames, FieldDetails addressFieldDetails, FieldDetails entityFieldDetails) {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
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
        List<String> miscSingleTableNames = Arrays.asList(ADDRESS_TABLE, MEDIA_TABLE, SYNC_TELEMETRY_TABLE);
        List<String> filteredTables = filterOutExistingQuestions(miscSingleTableNames);

        for (String tableName : filteredTables) {
            createQuestionForTable(tableName);
        }
    }

    private void createQuestionsForViews() {
        List<String> viewNames = Arrays.stream(QuestionName.values()).map(QuestionName::getViewName).collect(Collectors.toList());
        List<String> filteredViews = filterOutExistingQuestions(viewNames);

        for (String viewName : filteredViews) {
            createQuestionForTable(viewName);
        }
    }

    private void createCustomQuestions() {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        if (isQuestionMissing(QuestionName.NonVoidedIndividual.getQuestionName())) {
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonVoidedIndividual, VisualizationType.PIE, Collections.EMPTY_LIST);
        }
        if (isQuestionMissing(QuestionName.NonExitedNonVoidedProgram.getQuestionName())) {
            FilterCondition additionalFilterCondition = new FilterCondition(ConditionType.IS_NULL,
                    databaseRepository.getFieldDetailsByName(database, new TableDetails(QuestionName.NonExitedNonVoidedProgram.getViewName(), database.getName()),
                            new FieldDetails(PROGRAM_EXIT_DATE_TIME)).getId(), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(), null);
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonExitedNonVoidedProgram, VisualizationType.PIE, Arrays.asList(additionalFilterCondition));
        }
        updateGlobalDashboardWithCustomQuestions();
    }

    public void updateGlobalDashboardWithCustomQuestions() {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        List<Dashcard> dashCards = new ArrayList<>();
        dashCards.add(new Dashcard(-1, getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), -1, 0, FIRST_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForFirstDashCard(database)));
        dashCards.add(new Dashcard(-2, getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), -1, 0, SECOND_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForSecondDashCard(database)));

        List<Tabs> tabs = new ArrayList<>();
        tabs.add(new Tabs(-1, "Activity"));

        CollectionItem dashboard = metabaseDashboardRepository.getDashboard(getOrgCollection());
        metabaseDashboardRepository.updateDashboard(dashboard.getId(), new DashboardUpdateRequest(dashCards, createParametersForDashboard(), tabs));
    }

    private List<ParameterMapping> createDashCardParameterMappingForFirstDashCard(Database database) {
        List<ParameterMapping> firstDashCardParameterMapping = new ArrayList<>();
        firstDashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(new TableDetails(QuestionName.NonVoidedIndividual.getViewName(), database.getName()), new FieldDetails(FieldName.REGISTRATION_DATE.getName())), FieldType.DATE.getTypeName()))));
        return firstDashCardParameterMapping;
    }

    private List<ParameterMapping> createDashCardParameterMappingForSecondDashCard(Database database) {
        List<ParameterMapping> secondDashCardParameterMapping = new ArrayList<>();
        secondDashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(new TableDetails(QuestionName.NonExitedNonVoidedProgram.getViewName(), database.getName()), new FieldDetails(FieldName.ENROLMENT_DATE_TIME.getName())), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        return secondDashCardParameterMapping;
    }

    private List<Parameters> createParametersForDashboard() {
        List<Parameters> parameters = new ArrayList<>();
        parameters.add(new Parameters("Date Range", "all_options", "dateTimeId", "date/all-options", "date"));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            parameters.add(new Parameters(addressLevelTypeName, addressLevelTypeName, addressLevelTypeName, "string/starts-with", "string", "search"));
        }
        parameters.add(new Parameters("Program Name", "programName", "programName", "string/starts-with", "string", "search", false));
        parameters.add(new Parameters("Subject Type Name", "subjectTypeName", "subjectTypeName", "string/starts-with", "string", "search", false));
        return parameters;
    }

    public void addCollectionItems() throws InterruptedException {
        Organisation organisation = organisationService.getCurrentOrganisation();
        waitForSyncToComplete(organisation);

        //todo add field details and table details to request scope
        logger.info("Adding questions for subject types {}", organisation.getName());
        createQuestionsForSubjectTypes();
        logger.info("Adding questions for programs and encounters {}", organisation.getName());
        createQuestionsForProgramsAndEncounters();
        logger.info("Adding questions for misc single tables {}", organisation.getName());
        createQuestionsForMiscSingleTables();
        logger.info("Adding questions for views {}", organisation.getName());
        createQuestionsForViews();
        logger.info("Adding custom questions {}", organisation.getName());
        createCustomQuestions();
    }

    private void waitForSyncToComplete(Organisation organisation) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        logger.info("Waiting for metabase database sync {}", organisation.getName());
        while (true) {
            long timeSpent = System.currentTimeMillis() - startTime;
            long timeLeft = timeSpent - (MAX_WAIT_TIME_IN_SECONDS * 1000);
            if (!(timeLeft < 0)) break;
            SyncStatus syncStatus = this.getInitialSyncStatus();
            if (syncStatus != SyncStatus.COMPLETE) {
                Thread.sleep(EACH_SLEEP_DURATION * 2000);
                logger.info("Sync not complete after {} seconds, waiting for metabase database sync {}", timeSpent/1000, organisation.getName());
            } else {
                break;
            }
        }
    }

    public void syncDatabase() throws InterruptedException {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.reSyncSchema(database);
        databaseRepository.rescanFieldValues(database);
        this.waitForSyncToComplete(organisation);
    }
}
