package org.avni.server.dao.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.metabase.Database;
import org.avni.server.domain.metabase.MetabaseDatabaseInfo;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    public JsonNode getFields(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId + "/fields";
        return getForObject(url, JsonNode.class);
    }

    public JsonNode getInitialSyncStatus(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId;
        return getForObject(url, JsonNode.class);
    }

    public JsonNode getDataset(String requestBody) {
        String url = metabaseApiUrl + "/dataset";
        return postForObject(url, requestBody, JsonNode.class);
    }
}
