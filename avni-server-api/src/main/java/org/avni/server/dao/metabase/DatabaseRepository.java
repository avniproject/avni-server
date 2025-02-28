package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.S;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class DatabaseRepository extends MetabaseConnector {
    private final CollectionRepository collectionRepository;

    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder , CollectionRepository collectionRepository) {
        super(restTemplateBuilder);
        this.collectionRepository = collectionRepository;
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }

    public Database getDatabase(String organisationName, String organisationDbUser) {
        String url = metabaseApiUrl + "/database";

        String jsonResponse = getForObject(url, String.class);

        try {
            JsonNode rootNode = ObjectMapperSingleton.getObjectMapper().readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            for (JsonNode dbNode : dataArray) {
                Database db = ObjectMapperSingleton.getObjectMapper().treeToValue(dbNode, Database.class);
                if (db.getName().equals(organisationName) && db.getDetails().getUser().equals(organisationDbUser)) {
                    return db;
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve database", e);
        }
    }

    public Database getDatabase(Organisation organisation) {
        return getDatabase(organisation.getName(), organisation.getDbUser());
    }

    protected CollectionInfoResponse getCollectionForDatabase(Database database) {
        CollectionInfoResponse collectionByName = collectionRepository.getCollection(database.getName());
        if (Objects.isNull(collectionByName)) {
            throw new RuntimeException(String.format("Failed to fetch collection for database %s", database.getName()));
        }
        return collectionByName;
    }

    public FieldDetails getFieldDetailsByName(Database database, TableDetails tableDetails, FieldDetails fieldDetails) {
        List<FieldDetails> fieldsList = getFields(database);
        String snakeCaseTableName = S.toSnakeCase(tableDetails.getName());

        return fieldsList.stream()
                .filter(field -> snakeCaseTableName.equals(field.getTableName()) && fieldDetails.getName().equals(field.getName())
                        && (field.getSchema().equalsIgnoreCase(database.getName()) == (tableDetails.getSchema().equalsIgnoreCase(database.getName()))))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Field " + fieldDetails.getName() + " not found in table " + tableDetails.getName()));
    }

    private MetabaseDatabaseInfo getDatabaseDetails(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "?include=tables";
        String jsonResponse = getForObject(url, String.class);

        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, MetabaseDatabaseInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse database details", e);
        }
    }

    private List<FieldDetails> getFields(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "/fields";
        String jsonResponse = getForObject(url, String.class);

        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fields", e);
        }
    }

    public TableDetails findTableDetailsByName(Database database, TableDetails targetTable) {
        MetabaseDatabaseInfo databaseInfo = getDatabaseDetails(database);
        return databaseInfo.getTables().stream()
                .filter(tableDetail -> tableDetail.getName().equalsIgnoreCase(targetTable.getName())
                        && (tableDetail.getSchema().equalsIgnoreCase(database.getName()) == targetTable.getSchema().equalsIgnoreCase(database.getName())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Table with name " + targetTable.getName() + " not found."));
    }

    public DatabaseSyncStatus getInitialSyncStatus(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId();
        String jsonResponse = getForObject(url, String.class);
        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, DatabaseSyncStatus.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse sync status", e);
        }
    }

    private DatasetResponse getDataset(DatasetRequestBody requestBody) {
        String url = metabaseApiUrl + "/dataset";
        String jsonRequestBody = requestBody.toJson().toString();
        String jsonResponse = postForObject(url, jsonRequestBody, String.class);
        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, DatasetResponse.class);
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

    public void delete(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId();
        deleteForObject(url, Void.class);
    }
}
