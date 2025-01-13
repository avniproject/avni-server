package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.S;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class DatabaseRepository extends MetabaseConnector {
    private final ObjectMapper objectMapper;
    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }

    public Database getDatabaseByName(Database database) {
        String url = metabaseApiUrl + "/database";

        String jsonResponse = getForObject(url, String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            for (JsonNode dbNode : dataArray) {
                Database db = objectMapper.treeToValue(dbNode, Database.class);
                if (db.getName().equals(database.getName())) {
                    return db;
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve database", e);
        }
    }

    public List<CollectionItem> getExistingCollectionItems(int collectionId) {
        String url = metabaseApiUrl + "/collection/" + collectionId + "/items";
        String jsonResponse = getForObject(url, String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            List<CollectionItem> items = new ArrayList<>();
            for (JsonNode itemNode : dataArray) {
                CollectionItem item = new CollectionItem();
                item.setName(itemNode.get("name").asText());
                item.setId(itemNode.get("id").asInt());
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch collection items", e);
        }
    }

    public CollectionInfoResponse getCollectionByName(Database database) {
        String url = metabaseApiUrl + "/collection";
        try {
            String jsonResponse = getForObject(url, String.class);
            List<CollectionInfoResponse> collections = objectMapper.readValue(
                    jsonResponse, new TypeReference<List<CollectionInfoResponse>>() {
                    }
            );

            return collections.stream()
                    .filter(collection -> collection.getName().equals(database.getName()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve collection", e);
        }
    }

    public void createQuestionForTable(Database database, TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails originField, FieldDetails destinationField) {
        FieldDetails joinField1 = getFieldDetailsByName(database, addressTableDetails, originField);
        FieldDetails joinField2 = getFieldDetailsByName(database, tableDetails, destinationField);

        ArrayNode joinsArray = objectMapper.createArrayNode();
        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray)
                .forTable(tableDetails)
                .joinWith(addressTableDetails, joinField1, joinField2)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                getCollectionForDatabase(database).getIdAsInt()
        );

        postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    public void createQuestionForASingleTable(Database database, TableDetails tableDetails) {
        MetabaseQuery query = new MetabaseQueryBuilder(database, objectMapper.createArrayNode())
                .forTable(tableDetails)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                getCollectionForDatabase(database).getIdAsInt()
        );

        postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    public MetabaseQuery createAdvancedQuery(String primaryTableName, String secondaryTableName, QuestionConfig config, Database database) {
        TableDetails primaryTable = findTableDetailsByName(database, new TableDetails(primaryTableName));
        FieldDetails primaryField = getFieldDetailsByName(database, primaryTable, new FieldDetails(config.getPrimaryField()));

        TableDetails secondaryTable = findTableDetailsByName(database, new TableDetails(secondaryTableName));
        FieldDetails breakoutField = getFieldDetailsByName(database, secondaryTable, new FieldDetails(config.getBreakoutField()));

        return new MetabaseQueryBuilder(database, ObjectMapperSingleton.getObjectMapper().createArrayNode())
                .forTable(primaryTable)
                .addAggregation(config.getAggregationType())
                .addBreakout(breakoutField.getId(), breakoutField.getBaseType(), primaryField.getId())
                .addFilter(config.getFilters())
                .build();
    }

    public void createAdvancedQuestion(Database database) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout("name", "subject_type_id")
                .withFilters(
                        new FilterCondition(ConditionType.EQUAL, getFieldDetailsByName(database, new TableDetails("individual"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false),
                        new FilterCondition(ConditionType.EQUAL, getFieldDetailsByName(database, new TableDetails("subject_type"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false,getFieldDetailsByName(database, new TableDetails("individual"), new FieldDetails("subject_type_id")).getId())
                )
                .withVisualization(VisualizationType.PIE);
        MetabaseQuery query = createAdvancedQuery("individual", "subject_type", config, database);
        postQuestion(
                QuestionName.QUESTION_1.getQuestionName(),
                query,
                config,
                getCollectionForDatabase(database).getIdAsInt()
        );
    }

    public void createAdvancedQuestion2(Database database) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout("name", "program_id")
                .withFilters(
                        new FilterCondition(ConditionType.EQUAL, getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false),
                        new FilterCondition(ConditionType.IS_NULL, getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("program_exit_date_time")).getId() , FieldType.DATE_TIME_WITH_LOCAL_TZ.getTypeName(),null),
                        new FilterCondition(ConditionType.EQUAL, getFieldDetailsByName(database, new TableDetails("program"), new FieldDetails("is_voided")).getId() , FieldType.BOOLEAN.getTypeName(), false,getFieldDetailsByName(database, new TableDetails("program_enrolment"), new FieldDetails("program_id")).getId())
                )
                .withVisualization(VisualizationType.PIE);
        MetabaseQuery query = createAdvancedQuery("program_enrolment", "program", config, database);
        postQuestion(
                QuestionName.QUESTION_2.getQuestionName(),
                query,
                config,
                getCollectionForDatabase(database).getIdAsInt()
        );
    }

    public void postQuestion(String questionName, MetabaseQuery query, QuestionConfig config, int collectionId) {
        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                questionName,
                query,
                config.getVisualizationType(),
                null,
                objectMapper.createObjectNode(),
                collectionId
        );
        postForObject(metabaseApiUrl + "/card", requestBody.toJson(), ObjectNode.class);
    }

    private CollectionInfoResponse getCollectionForDatabase(Database database) {
        CollectionInfoResponse collectionByName = getCollectionByName(database);
        if (Objects.isNull(collectionByName)) {
            throw new RuntimeException(String.format("Failed to fetch collection for database %s", database.getName()));
        }
        return collectionByName;
    }

    public FieldDetails getFieldDetailsByName(Database database, TableDetails tableDetails, FieldDetails fieldDetails) {
        List<FieldDetails> fieldsList = getFields(database);
        String snakeCaseTableName = S.toSnakeCase(tableDetails.getName());

        return fieldsList.stream()
                .filter(field -> snakeCaseTableName.equals(field.getTableName()) && fieldDetails.getName().equals(field.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Field " + fieldDetails.getName() + " not found in table " + tableDetails.getName()));
    }

    public MetabaseDatabaseInfo getDatabaseDetails(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "?include=tables";
        String jsonResponse = getForObject(url, String.class);

        try {
            return objectMapper.readValue(jsonResponse, MetabaseDatabaseInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse database details", e);
        }
    }

    public List<FieldDetails> getFields(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "/fields";
        String jsonResponse = getForObject(url, String.class);

        try {
            return objectMapper.readValue(jsonResponse, new TypeReference<List<FieldDetails>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fields", e);
        }
    }

    public TableDetails findTableDetailsByName(Database database, TableDetails targetTable) {
        MetabaseDatabaseInfo databaseInfo = getDatabaseDetails(database);
        return databaseInfo.getTables().stream()
                .filter(tableDetail -> tableDetail.getName().equalsIgnoreCase(targetTable.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Table with name " + targetTable.getName() + " not found."));
    }

    public DatabaseSyncStatus getInitialSyncStatus(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId();
        String jsonResponse = getForObject(url, String.class);
        try {
            return objectMapper.readValue(jsonResponse, DatabaseSyncStatus.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse sync status", e);
        }
    }

    public DatasetResponse getDataset(DatasetRequestBody requestBody) {
        String url = metabaseApiUrl + "/dataset";
        String jsonRequestBody = requestBody.toJson().toString();
        String jsonResponse = postForObject(url, jsonRequestBody, String.class);
        try {
            return objectMapper.readValue(jsonResponse, DatasetResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse dataset response", e);
        }
    }

    public DatasetResponse findAll(TableDetails table, Database database) {
        DatasetRequestBody requestBody = createRequestBodyForDataset(database, table);
        return getDataset(requestBody);
    }

    private DatasetRequestBody createRequestBodyForDataset(Database database, TableDetails table) {
        return new DatasetRequestBody(database, table);
    }
}
