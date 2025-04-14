package org.avni.server.service.metabase;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.etl.TableMetaDataRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.MetabaseDashboardRepository;
import org.avni.server.dao.metabase.MetabaseDatabaseRepository;
import org.avni.server.dao.metabase.QuestionRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
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
    private final CollectionRepository collectionRepository;
    private final QuestionRepository questionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationService organisationService;
    private final TableMetaDataRepository tableMetaDataRepository;

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final long MAX_WAIT_TIME_IN_SECONDS = 300;
    private static final long EACH_SLEEP_DURATION = 3;

    @Autowired
    public DatabaseService(MetabaseDatabaseRepository databaseRepository, MetabaseService metabaseService, CollectionRepository collectionRepository, QuestionRepository questionRepository, MetabaseDashboardRepository metabaseDashboardRepository, AddressLevelTypeRepository addressLevelTypeRepository, OrganisationService organisationService, TableMetaDataRepository tableMetaDataRepository) {
        this.databaseRepository = databaseRepository;
        this.collectionRepository = collectionRepository;
        this.questionRepository = questionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationService = organisationService;
        this.tableMetaDataRepository = tableMetaDataRepository;
    }

    private CollectionInfoResponse getOrgCollection() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        return collectionRepository.getCollection(organisation);
    }

    public SyncStatus getInitialSyncStatus() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
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
        String schemaName = UserContextHolder.getOrganisation().getSchemaName();
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(tableName, schemaName));
        questionRepository.createQuestionForASingleTable(database, fetchedTableDetails);
    }

    private void createQuestionsForEntities(List<String> entityNames, FieldDetails addressFieldDetails, FieldDetails entityFieldDetails) {
        String schemaName = UserContextHolder.getOrganisation().getSchemaName();
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        TableDetails fetchedAddressTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(ADDRESS_TABLE, schemaName));
        List<String> filteredEntities = filterOutExistingQuestions(entityNames);

        for (String entityName : filteredEntities) {
            TableDetails fetchedEntityTableDetails = databaseRepository.findTableDetailsByName(database, new TableDetails(entityName, schemaName));
            createQuestionForTable(fetchedEntityTableDetails, fetchedAddressTableDetails, addressFieldDetails, entityFieldDetails);
        }
    }

    private void createQuestionsForSubjectTypes(Organisation organisation) {
        List<String> subjectTypeNames = tableMetaDataRepository.getSubjectTypeNames(organisation);
        FieldDetails addressFieldDetails = new FieldDetails(ID);
        FieldDetails subjectFieldDetails = new FieldDetails(ADDRESS_ID);
        createQuestionsForEntities(subjectTypeNames, addressFieldDetails, subjectFieldDetails);
    }

    private void createQuestionsForProgramsAndEncounters(Organisation organisation) {
        List<String> programAndEncounterNames = tableMetaDataRepository.getProgramAndEncounterNames(organisation);
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
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonVoidedIndividual, VisualizationType.PIE, Collections.EMPTY_LIST, false);
        }
        if (isQuestionMissing(QuestionName.NonExitedNonVoidedProgram.getQuestionName())) {
            FieldDetails field = databaseRepository.getOrgSchemaField(database, QuestionName.NonExitedNonVoidedProgram.getViewName(), PROGRAM_EXIT_DATE_TIME);
            FilterCondition additionalFilterCondition = new FilterCondition(ConditionType.IS_NULL,
                    field.getId(), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(), null);
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.NonExitedNonVoidedProgram, VisualizationType.PIE, Arrays.asList(additionalFilterCondition), false);
        }
        if (isQuestionMissing(QuestionName.DueVisits.getQuestionName())) {
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.DueVisits, VisualizationType.PIE, Collections.EMPTY_LIST, true);
        }
        if (isQuestionMissing(QuestionName.CompletedVisits.getQuestionName())) {
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.CompletedVisits, VisualizationType.PIE, Collections.EMPTY_LIST, true);
        }
        if (isQuestionMissing(QuestionName.OverDueVisits.getQuestionName())) {
            questionRepository.createCustomQuestionOfVisualization(database, QuestionName.OverDueVisits, VisualizationType.PIE, Collections.EMPTY_LIST, true);
        }
        updateGlobalDashboardWithCustomQuestions();
    }

    public void updateGlobalDashboardWithCustomQuestions() {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
        List<Dashcard> dashCards = new ArrayList<>();
        dashCards.add(new Dashcard(-1, getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), -1, 0, FIRST_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForFirstDashCard(database)));
        dashCards.add(new Dashcard(-2, getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), -1, 0, SECOND_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForSecondDashCard(database)));
        dashCards.add(new Dashcard(-3, getCardIdByQuestionName(QuestionName.DueVisits.getQuestionName()), -1, 1, FIRST_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForThirdDashCard(database)));
        dashCards.add(new Dashcard(-4, getCardIdByQuestionName(QuestionName.CompletedVisits.getQuestionName()), -1, 1, SECOND_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForFourthDashCard(database)));
        dashCards.add(new Dashcard(-5, getCardIdByQuestionName(QuestionName.OverDueVisits.getQuestionName()), -1, 2, FIRST_CARD_COL_IDX, 12, 8, Collections.emptyMap(), createDashCardParameterMappingForFifthDashCard(database)));

        List<Tabs> tabs = new ArrayList<>();
        tabs.add(new Tabs(-1, "Activity"));

        CollectionItem dashboard = metabaseDashboardRepository.getDashboard(getOrgCollection());
        metabaseDashboardRepository.updateDashboard(dashboard.getId(), new DashboardUpdateRequest(dashCards, createParametersForDashboard(), tabs));
    }

    private String getSchemaName() {
        Organisation organisation = UserContextHolder.getOrganisation();
        return organisation.getSchemaName();
    }
    // Any card added to a dashboard in metabase is called a dashcard and the dashboard level filters are linked via parameter mappings
    private List<ParameterMapping> createDashCardParameterMappingForFirstDashCard(Database database) {
        List<ParameterMapping> dashCardParameterMapping = new ArrayList<>();
        int fieldId = getFieldId(database, getSchemaName(), QuestionName.NonVoidedIndividual.getViewName(), FieldName.REGISTRATION_DATE.getName());
        dashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(fieldId, FieldType.DATE.getTypeName()))));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            dashCardParameterMapping.add(new ParameterMapping(addressLevelTypeName, getCardIdByQuestionName(QuestionName.NonVoidedIndividual.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.NonVoidedIndividual.getViewName(),addressLevelTypeName), FieldType.TEXT.getTypeName()))));
        }
        return dashCardParameterMapping;
    }

    private List<ParameterMapping> createDashCardParameterMappingForSecondDashCard(Database database) {
        List<ParameterMapping> dashCardParameterMapping = new ArrayList<>();
        int fieldId = getFieldId(database, getSchemaName(), QuestionName.NonExitedNonVoidedProgram.getViewName(), FieldName.ENROLMENT_DATE_TIME.getName());
        dashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(fieldId, FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            dashCardParameterMapping.add(new ParameterMapping(addressLevelTypeName, getCardIdByQuestionName(QuestionName.NonExitedNonVoidedProgram.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.NonExitedNonVoidedProgram.getViewName(),addressLevelTypeName), FieldType.TEXT.getTypeName()))));
        }
        return dashCardParameterMapping;
    }

    private List<ParameterMapping> createDashCardParameterMappingForThirdDashCard(Database database) {
        List<ParameterMapping> dashCardParameterMapping = new ArrayList<>();
        dashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.DueVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.DueVisits.getViewName(),FieldName.EARLIEST_VISIT_DATE_TIME.getName()), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            dashCardParameterMapping.add(new ParameterMapping(addressLevelTypeName, getCardIdByQuestionName(QuestionName.DueVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.DueVisits.getViewName(),addressLevelTypeName), FieldType.TEXT.getTypeName()))));
        }
        return dashCardParameterMapping;
    }

    private List<ParameterMapping> createDashCardParameterMappingForFourthDashCard(Database database) {
        List<ParameterMapping> dashCardParameterMapping = new ArrayList<>();
        dashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.CompletedVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.CompletedVisits.getViewName(),FieldName.ENCOUNTER_DATE_TIME.getName()), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            dashCardParameterMapping.add(new ParameterMapping(addressLevelTypeName, getCardIdByQuestionName(QuestionName.CompletedVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.CompletedVisits.getViewName(),addressLevelTypeName), FieldType.TEXT.getTypeName()))));
        }
        return dashCardParameterMapping;
    }

    private List<ParameterMapping> createDashCardParameterMappingForFifthDashCard(Database database) {
        List<ParameterMapping> dashCardParameterMapping = new ArrayList<>();
        dashCardParameterMapping.add(new ParameterMapping("dateTimeId", getCardIdByQuestionName(QuestionName.OverDueVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.OverDueVisits.getViewName(), FieldName.EARLIEST_VISIT_DATE_TIME.getName()), FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName()))));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            dashCardParameterMapping.add(new ParameterMapping(addressLevelTypeName, getCardIdByQuestionName(QuestionName.OverDueVisits.getQuestionName()), new Target(MetabaseTargetType.DIMENSION, new FieldTarget(getFieldId(database, getSchemaName(),QuestionName.OverDueVisits.getViewName(),addressLevelTypeName), FieldType.TEXT.getTypeName()))));
        }
        return dashCardParameterMapping;
    }

    private int getFieldId(Database database, String schemaName, String viewName, String fieldName) {
        FieldDetails field = databaseRepository.getField(database, schemaName, viewName, fieldName);
        return field.getId();
    }

    private List<Parameters> createParametersForDashboard() {
        List<Parameters> parameters = new ArrayList<>();
        parameters.add(new Parameters("Date Range", "all_options", "dateTimeId", "date/all-options", "date"));
        List<String> addressLevelTypeNames = addressLevelTypeRepository.getAllNames();
        for (String addressLevelTypeName : addressLevelTypeNames) {
            parameters.add(new Parameters(addressLevelTypeName, addressLevelTypeName, addressLevelTypeName, "string/starts-with", "string", "search"));
        }
        return parameters;
    }

    public void addCollectionItems() {
        Organisation organisation = organisationService.getCurrentOrganisation();

        //todo add field details and table details to request scope
        logger.info("Adding questions for subject types {}", organisation.getName());
        createQuestionsForSubjectTypes(organisation);
        logger.info("Adding questions for programs and encounters {}", organisation.getName());
        createQuestionsForProgramsAndEncounters(organisation);
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
                logger.info("Sync not complete after {} seconds, waiting for metabase database sync {}", timeSpent / 1000, organisation.getName());
            } else {
                break;
            }
        }
    }

    public void syncDatabase() throws InterruptedException {
        Organisation organisation = organisationService.getCurrentOrganisation();
        this.waitForSyncToComplete(organisation);
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.reSyncSchema(database);
        databaseRepository.rescanFieldValues(database);
        this.waitForSyncToComplete(organisation);
    }
}
