package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.metabase.*;
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

    public MetabaseDatabaseInfo getDatabaseDetails(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId + "?include=tables";
        String jsonResponse = getForObject(url, String.class);
        try {
            return objectMapper.readValue(jsonResponse, MetabaseDatabaseInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse database details", e);
        }
    }

    public List<FieldDetails> getFields(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId + "/fields";
        String jsonResponse = getForObject(url, String.class);
        try {
            return objectMapper.readValue(jsonResponse, new TypeReference<List<FieldDetails>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fields", e);
        }
    }

    public DatabaseSyncStatus getInitialSyncStatus(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId;
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
}
