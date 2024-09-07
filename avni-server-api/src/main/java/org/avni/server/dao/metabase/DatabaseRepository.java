package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.S;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@Repository
public class DatabaseRepository extends MetabaseConnector {
    private final ObjectMapper objectMapper;
    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
        this.objectMapper = new ObjectMapper();
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }

    public Database getDatabaseById(Database database) {
        int id = database.getId();
        String url = metabaseApiUrl + "/database/" + id;
        try {
            String jsonResponse = getForObject(url, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonResponse, Database.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve database with ID " + id, e);
        }
    }

    public Database getDatabaseByName(String name) {
        String url = metabaseApiUrl + "/database";

        String jsonResponse = getForObject(url, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            for (JsonNode dbNode : dataArray) {
                Database db = objectMapper.treeToValue(dbNode, Database.class);
                if (db.getName().equals(name)) {
                    return db;
                }
            }
            throw new RuntimeException("Database with name " + name + " not found.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve database", e);
        }
    }

    public CollectionInfoResponse getCollectionByName(String collectionName) {
        String url = metabaseApiUrl + "/collection";
        try {
            String jsonResponse = getForObject(url, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            List<CollectionInfoResponse> collections = objectMapper.readValue(
                    jsonResponse, new TypeReference<List<CollectionInfoResponse>>() {}
            );

            return collections.stream()
                    .filter(collection -> collection.getName().equals(collectionName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Collection with name " + collectionName + " not found."));
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve collection", e);
        }
    }

    public void createQuestionForTable(Database database, TableDetails tableDetails, TableDetails addressTableDetails, String addressField, String tableField) {
        FieldDetails joinField1 = getFieldDetailsByName(database, addressTableDetails.getName(), addressField);
        FieldDetails joinField2 = getFieldDetailsByName(database, tableDetails.getName(), tableField);

        ArrayNode joinsArray = objectMapper.createArrayNode();
        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray, objectMapper)
                .forTable(tableDetails)
                .joinWith(addressTableDetails, joinField1, joinField2)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                "Address + " + tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                getCollectionByName(database.getName()).getIdAsInt(),
                CardType.QUESTION
        );

        postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
    }

    public FieldDetails getFieldDetailsByName(Database database, String tableName, String fieldName) {
        List<FieldDetails> fieldsList = getFields(database);
        String snakeCaseTableName = S.toSnakeCase(tableName);

        return fieldsList.stream()
                .filter(field -> snakeCaseTableName.equals(field.getTableName()) && fieldName.equals(field.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Field " + fieldName + " not found in table " + tableName));
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

    public DatasetResponse getDataset(String requestBody) {
        String url = metabaseApiUrl + "/dataset";
        String jsonResponse = postForObject(url, requestBody, String.class);
        try {
            return objectMapper.readValue(jsonResponse, DatasetResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse dataset response", e);
        }
    }

    public DatasetResponse findAll(TableDetails table, Database database) {
        String requestBody = createRequestBodyForDataset(database, table);
        return getDataset(requestBody);
    }

    private String createRequestBodyForDataset(Database database,TableDetails table) {
        return "{\"database\":" + database.getId() + ",\"query\":{\"source-table\":" + table.getId() + "},\"type\":\"query\",\"parameters\":[]}";
    }
}
