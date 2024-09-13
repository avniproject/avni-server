package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.S;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
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
                    jsonResponse, new TypeReference<List<CollectionInfoResponse>>() {}
            );

            return collections.stream()
                    .filter(collection -> collection.getName().equals(database.getName()))
                    .findFirst()
                    .orElseThrow(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve collection", e);
        }
    }

    public void createQuestionForTable(Database database, TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails originField, FieldDetails destinationField) {
        FieldDetails joinField1 = getFieldDetailsByName(database, addressTableDetails, originField);
        FieldDetails joinField2 = getFieldDetailsByName(database, tableDetails, destinationField);

        ArrayNode joinsArray = objectMapper.createArrayNode();
        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray, objectMapper)
                .forTable(tableDetails)
                .joinWith(addressTableDetails, joinField1, joinField2)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                getCollectionByName(database).getIdAsInt()
        );

        postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
    }

    public void createQuestionForASingleTable(Database database, TableDetails tableDetails) {
        MetabaseQuery query = new MetabaseQueryBuilder(database, objectMapper.createArrayNode(), objectMapper)
                .forTable(tableDetails)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                getCollectionByName(database).getIdAsInt()
        );

        postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
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
        String jsonRequestBody = requestBody.toJson(objectMapper).toString();
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
