package org.avni.server.dao.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.metabase.Database;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseRepository extends MetabaseConnector {
    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }

    public JsonNode getDatabaseDetails(int databaseId) {
        String url = metabaseApiUrl + "/database/" + databaseId + "?include=tables";
        return getForObject(url, JsonNode.class);
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
