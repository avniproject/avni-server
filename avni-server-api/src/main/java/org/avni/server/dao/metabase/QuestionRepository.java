package org.avni.server.dao.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.avni.server.domain.JoinTableConfig;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class QuestionRepository extends MetabaseConnector {
    public static final String IS_VOIDED = "is_voided";

    private final DatabaseRepository databaseRepository;

    public QuestionRepository(RestTemplateBuilder restTemplateBuilder, DatabaseRepository databaseRepository) {
        super(restTemplateBuilder);
        this.databaseRepository = databaseRepository;
    }

    public void createCustomQuestionOfVisualization(Database database, QuestionName question, VisualizationType visualizationType, List<FilterCondition> additionalFilterConditions) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout(question.getBreakoutField(), question.getPrimaryField())
                .withFilters(getFilterConditions(additionalFilterConditions, database, question).toArray(FilterCondition[]::new))
                .withVisualization(visualizationType);
        MetabaseQuery query = createAdvancedQuery(question.getPrimaryTableName(), question.getSecondaryTableName(), config, database);
        postQuestion(
                question.getQuestionName(),
                query,
                config,
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );
    }

    private List<FilterCondition> getFilterConditions(List<FilterCondition> additionalFilterConditions, Database database, QuestionName question) {
        return ImmutableList.<FilterCondition>builder()
                .addAll(List.of(new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails(question.getPrimaryTableName()), new FieldDetails(IS_VOIDED)).getId(), FieldType.BOOLEAN.getTypeName(), false),
                        new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails(question.getSecondaryTableName()), new FieldDetails(IS_VOIDED)).getId(), FieldType.BOOLEAN.getTypeName(), false, databaseRepository.getFieldDetailsByName(database, new TableDetails(question.getPrimaryTableName()), new FieldDetails(question.getPrimaryField())).getId())))
                .addAll(additionalFilterConditions).build();
    }

    private void postQuestion(String questionName, MetabaseQuery query, QuestionConfig config, int collectionId) {
        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                questionName,
                query,
                config.getVisualizationType(),
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                collectionId
        );
        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), ObjectNode.class);
    }

    public void createQuestionForTable(Database database, TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails originField, FieldDetails destinationField) {
        FieldDetails joinField1 = databaseRepository.getFieldDetailsByName(database, addressTableDetails, originField);
        FieldDetails joinField2 = databaseRepository.getFieldDetailsByName(database, tableDetails, destinationField);

        ArrayNode joinsArray = ObjectMapperSingleton.getObjectMapper().createArrayNode();
        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray)
                .forTable(tableDetails)
                .joinWith(addressTableDetails, joinField1, joinField2)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    public void createQuestionForASingleTable(Database database, TableDetails tableDetails) {
        MetabaseQuery query = new MetabaseQueryBuilder(database, ObjectMapperSingleton.getObjectMapper().createArrayNode())
                .forTable(tableDetails)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    private MetabaseQuery createAdvancedQuery(String primaryTableName, String secondaryTableName, QuestionConfig config, Database database) {
        TableDetails primaryTable = databaseRepository.findTableDetailsByName(database, new TableDetails(primaryTableName));
        FieldDetails primaryField = databaseRepository.getFieldDetailsByName(database, primaryTable, new FieldDetails(config.getPrimaryField()));

        TableDetails secondaryTable = databaseRepository.findTableDetailsByName(database, new TableDetails(secondaryTableName));
        FieldDetails breakoutField = databaseRepository.getFieldDetailsByName(database, secondaryTable, new FieldDetails(config.getBreakoutField()));

        return new MetabaseQueryBuilder(database, ObjectMapperSingleton.getObjectMapper().createArrayNode())
                .forTable(primaryTable)
                .addAggregation(config.getAggregationType())
                .addBreakout(breakoutField.getId(), breakoutField.getBaseType(), primaryField.getId())
                .addFilter(config.getFilters())
                .build();
    }

    public void createQuestionForTableWithMultipleJoins(Database database, TableDetails tableDetails, List<JoinTableConfig> joinTableConfigs,
                                                        List<FieldDetails> primaryTableFields) {
        ArrayNode joinsArray = ObjectMapperSingleton.getObjectMapper().createArrayNode();
        MetabaseQueryBuilder metabaseQueryBuilder = new MetabaseQueryBuilder(database, joinsArray)
                .forTable(tableDetails, primaryTableFields);

        for (JoinTableConfig joinTableConfig : joinTableConfigs) {
            FieldDetails joinField1 = databaseRepository.getFieldDetailsByName(database, joinTableConfig.getJoinTargetTable(), joinTableConfig.getOriginField());
            FieldDetails joinField2 = databaseRepository.getFieldDetailsByName(database, Objects.isNull(joinTableConfig.getAlternateJoinSourceTable()) ?
                    tableDetails : joinTableConfig.getAlternateJoinSourceTable(), joinTableConfig.getDestinationField());
            metabaseQueryBuilder.joinWith(joinTableConfig.getJoinTargetTable(), joinField1, joinField2);
        }

        MetabaseQuery query = metabaseQueryBuilder.build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                tableDetails.getDescription(),
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }
}
