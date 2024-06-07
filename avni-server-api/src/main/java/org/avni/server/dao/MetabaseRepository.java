package org.avni.server.dao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Repository
public class MetabaseRepository {

    private final Logger logger = LoggerFactory.getLogger(MetabaseRepository.class);
    private final RestTemplate restTemplate;

    @Value("${metabase.api.key}")
    private String apiKey;

    @Value("${metabase.api.url}")
    private String metabaseApiUrl;

    @Value("${database.host}")
    private String dbHost;

    @Value("${database.port}")
    private String dbPort;

    @Value("${database.name}")
    private String dbName;

    @Value("${database.engine}")
    private String dbEngine;

    public MetabaseRepository(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public int createDatabase(String dbUser) {
        String url = metabaseApiUrl + "/database";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> details = new HashMap<>();
        details.put("host", dbHost);
        details.put("port", dbPort);
        details.put("db", dbName);
        details.put("user", dbUser);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("engine", dbEngine);
        requestBody.put("name", dbUser);
        requestBody.put("details", details);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create database for user: {}", dbUser);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        logger.info("Response from Metabase API (create database): " + response.getBody());

        return (Integer) response.getBody().get("id");
    }

    public int createCollection(String name) {
        String url = metabaseApiUrl + "/collection";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("description", name + " collection");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create collection for name: {}", name);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        logger.info("Response from Metabase API (create collection): " + response.getBody());

        return (Integer) response.getBody().get("id");
    }

    public int createPermissionsGroup(String name) {
        String url = metabaseApiUrl + "/permissions/group";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Request body for creating permissions group: {}", requestBody);
        logger.info("Sending request to create permissions group for name: {}", name);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        logger.info("Response from Metabase API (create permissions group): " + response.getBody());
        return (Integer) response.getBody().get("id");
    }

    public void assignDatabasePermissions(int groupId, int databaseId) {
        logger.info("Assigning database permissions for group ID: {}", groupId);
        String url = metabaseApiUrl + "/permissions/graph";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> permissionsGraph = response.getBody();

        Map<String, Object> groups = (Map<String, Object>) permissionsGraph.get("groups");
        if (groups == null) {
            throw new RuntimeException("Groups not found in the permissions graph.");
        }

        Map<String, Object> databasePermissions = new HashMap<>();
        databasePermissions.put("data", new HashMap<String, String>() {{
            put("schemas", "all");
        }});

        if (!groups.containsKey(String.valueOf(groupId))) {
            groups.put(String.valueOf(groupId), new HashMap<>());
        }
        Map<String, Object> groupPermissions = (Map<String, Object>) groups.get(String.valueOf(groupId));
        groupPermissions.put(String.valueOf(databaseId), databasePermissions);

        if (groups.containsKey("1")) {
            Map<String, Object> group1Permissions = (Map<String, Object>) groups.get("1");
            if (group1Permissions.containsKey(String.valueOf(databaseId))) {
                Map<String, Object> group1DatabasePermissions = (Map<String, Object>) group1Permissions.get(String.valueOf(databaseId));
                if (group1DatabasePermissions.containsKey("data")) {
                    Map<String, String> dataPermissions = (Map<String, String>) group1DatabasePermissions.get("data");
                    dataPermissions.put("native", "none");
                    dataPermissions.put("schemas", "none");
                }
            }
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(permissionsGraph, headers);
        response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
        logger.info("Response from Metabase API (assign database permissions): " + response.getBody());
    }

    public void updateCollectionPermissions(int groupId, int collectionId) {
        logger.info("Updating collection permissions for group ID: {} and collection ID: {}", groupId, collectionId);
        String graphUrl = metabaseApiUrl + "/collection/graph";

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);

        ResponseEntity<Map> graphResponse = restTemplate.exchange(graphUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> collectionGraph = graphResponse.getBody();

        logger.info("Current collection permissions graph: {}", collectionGraph);

        Map<String, Map<String, String>> groups = (Map<String, Map<String, String>>) collectionGraph.get("groups");

        groups.computeIfAbsent(String.valueOf(groupId), k -> new HashMap<>());

        Map<String, String> groupPermissions = groups.get(String.valueOf(groupId));
        groupPermissions.put(String.valueOf(collectionId), "write");

        if (groups.containsKey("1")) {
            Map<String, String> group1Permissions = (Map<String, String>) groups.get("1");
            group1Permissions.put(String.valueOf(collectionId), "none"); // Set the permission for the specified collection ID to "none"
        }

        logger.info("Updated collection permissions graph: {}", collectionGraph);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(collectionGraph, headers);
        graphResponse = restTemplate.exchange(graphUrl, HttpMethod.PUT, entity, Map.class);
        logger.info("Response from Metabase API (update collection permissions): " + graphResponse.getBody());
    }
}
