package org.avni.server.dao.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository extends MetabaseConnector {
    private final DatabaseRepository databaseRepository;

    public QuestionRepository(RestTemplateBuilder restTemplateBuilder , DatabaseRepository databaseRepository) {
        super(restTemplateBuilder);
        this.databaseRepository = databaseRepository;
    }

    public void createSubjectTypeIndividualQuestion(Database database) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout("name", "subject_type_id")
                .withFilters(
                        new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails("individual"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false),
                        new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails("subject_type"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false,databaseRepository.getFieldDetailsByName(database, new TableDetails("individual"), new FieldDetails("subject_type_id")).getId())
                )
                .withVisualization(VisualizationType.PIE);
        MetabaseQuery query = createAdvancedQuery("individual", "subject_type", config, database);
        postQuestion(
                QuestionName.QUESTION_1.getQuestionName(),
                query,
                config,
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );
    }

    public void createProgramEnrollmentsQuestion(Database database) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout("name", "program_id")
                .withFilters(
                        new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false),
                        new FilterCondition(ConditionType.IS_NULL, databaseRepository.getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("program_exit_date_time")).getId() , FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(),null),
                        new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails("program"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false,databaseRepository.getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("program_id")).getId())
                )
                .withVisualization(VisualizationType.PIE);
        MetabaseQuery query = createAdvancedQuery("program_enrolment", "program", config, database);
        postQuestion(
                QuestionName.QUESTION_2.getQuestionName(),
                query,
                config,
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );
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
}
