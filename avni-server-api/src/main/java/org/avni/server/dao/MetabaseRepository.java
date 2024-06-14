package org.avni.server.dao;

import org.avni.server.domain.metabase.Collection;
import org.avni.server.domain.metabase.CollectionPermissions;
import org.avni.server.domain.metabase.CollectionResponse;
import org.avni.server.domain.metabase.Database;
import org.avni.server.domain.metabase.DatabaseResponse;
import org.avni.server.domain.metabase.Permissions;
import org.avni.server.domain.metabase.PermissionsGroup;
import org.avni.server.domain.metabase.PermissionsGroupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Repository
public class MetabaseRepository {

    private final RestTemplate restTemplate;

    @Value("${metabase.api.key}")
    private String apiKey;

    @Value("${metabase.api.url}")
    private String metabaseApiUrl;

    public MetabaseRepository(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public int createDatabase(Database database) {
        String url = metabaseApiUrl + "/database";
        HttpEntity<Database> entity = createHttpEntity(database);
        DatabaseResponse response = restTemplate.postForObject(url, entity, DatabaseResponse.class);
        return response.getId();
    }

    public int createCollection(Collection collection) {
        String url = metabaseApiUrl + "/collection";
        HttpEntity<Collection> entity = createHttpEntity(collection);
        CollectionResponse response = restTemplate.postForObject(url, entity, CollectionResponse.class);
        return response.getId();
    }

    public int createPermissionsGroup(PermissionsGroup permissionsGroup) {
        String url = metabaseApiUrl + "/permissions/group";
        HttpEntity<PermissionsGroup> entity = createHttpEntity(permissionsGroup);
        PermissionsGroupResponse response = restTemplate.postForObject(url, entity, PermissionsGroupResponse.class);
        return response.getId();
    }

    public Map<String, Object> getPermissionsGraph() {
        String url = metabaseApiUrl + "/permissions/graph";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createHttpEntity(null), Map.class);
        return response.getBody();
    }

    public Map<String, Object> getCollectionPermissionsGraph() {
        String url = metabaseApiUrl + "/collection/graph";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createHttpEntity(null), Map.class);
        return response.getBody();
    }

    public void assignDatabasePermissions(Permissions permissions, int groupId, int databaseId) {
        permissions.updatePermissionsGraph(groupId, databaseId);
        String url = metabaseApiUrl + "/permissions/graph";
        sendPutRequest(url, permissions.getPermissionsGraph());
    }

    public void updateCollectionPermissions(CollectionPermissions collectionPermissions, int groupId, int collectionId) {
        collectionPermissions.updatePermissionsGraph(groupId, collectionId);
        String url = metabaseApiUrl + "/collection/graph";
        sendPutRequest(url, collectionPermissions.getPermissionsGraph());
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    private <T> HttpEntity<T> createHttpEntity(T body) {
        HttpHeaders headers = getHeaders();
        return new HttpEntity<>(body, headers);
    }

    private void sendPutRequest(String url, Map<String, Object> requestBody) {
        HttpEntity<Map<String, Object>> entity = createHttpEntity(requestBody);
        restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
    }
}
