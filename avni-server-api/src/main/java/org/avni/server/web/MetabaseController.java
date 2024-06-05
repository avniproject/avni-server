package org.avni.server.web;

import org.avni.server.service.OrganisationService;
import org.avni.server.service.OrganisationService.OrganisationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {

    private final Logger logger = LoggerFactory.getLogger(MetabaseController.class);
    private final RestTemplate restTemplate;
    private final OrganisationService organisationService;

    @Value("${metabase.api.key}")
    private String apiKey;

    public MetabaseController(RestTemplate restTemplate, OrganisationService organisationService) {
        this.restTemplate = restTemplate;
        this.organisationService = organisationService;
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupMetabase() {
        logger.info("Received request to setup Metabase");
        List<OrganisationDTO> organisations = organisationService.getOrganisations();

        for (OrganisationDTO organisation : organisations) {
            String name = organisation.getName();
            String dbUser = organisation.getDbUser();

            try {
                logger.info("Setting up Metabase for organisation: {}", name);
                // Step 1: Create Database (with constant database name)
                int databaseId = createDatabase(dbUser);

                // Step 2: Create Collection and get collectionId
                int collectionId = createCollection(name);

                // Step 3: Create Permissions Group
                int groupId = createPermissionsGroup(name);

                // Step 4: Assign Database Permissions to the Group
                assignDatabasePermissions(groupId,databaseId);

                // Step 5: Update Collection Permissions to Include the Group
                updateCollectionPermissions(groupId, collectionId);

            } catch (Exception e) {
                logger.error("Error setting up Metabase for organisation: " + name, e);
                return ResponseEntity.status(500).body("Failed to setup Metabase for organisation: " + name);
            }
        }

        return ResponseEntity.ok("Metabase setup completed for all organisations.");
    }

    private int createDatabase(String dbUser) {
        String url = "http://localhost:3000/api/database";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> details = new HashMap<>();
        details.put("host", "localhost");
        details.put("port", "5432");  
        details.put("db", "openchs");  
        details.put("user", dbUser);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("engine", "postgres");
        requestBody.put("name", dbUser);  
        requestBody.put("details", details);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create database for user: {}", dbUser);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        logger.info("Response from Metabase API (create database): " + response.getBody());

        return (Integer) response.getBody().get("id");
    }

    private int createCollection(String name) {
        String url = "http://localhost:3000/api/collection";
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

    private int createPermissionsGroup(String name) {
        String url = "http://localhost:3000/api/permissions/group";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Request body for creating permissions group: {}", requestBody);
        logger.info("Sending request to create permissions group for name: {}", name);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            logger.info("Response from Metabase API (create permissions group): " + response.getBody());
            return (Integer) response.getBody().get("id");
        } catch (Exception e) {
            logger.error("Error creating permissions group for name: " + name, e);
            throw new RuntimeException("Failed to create permissions group for name: " + name, e);
        }
    }

    private void assignDatabasePermissions(int groupId, int databaseId) {
        logger.info("Assigning database permissions for group ID: {}", groupId);
        String url = "http://localhost:3000/api/permissions/graph";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        try {
            // Fetch the updated permissions graph
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> permissionsGraph = response.getBody();

            // Log the fetched permissions graph
            logger.info("Fetched Permissions Graph: {}", permissionsGraph);

            // Modify the permissions graph to include the new group with database permissions
            Map<String, Object> groups = (Map<String, Object>) permissionsGraph.get("groups");
            if (groups == null) {
                throw new RuntimeException("Groups not found in the permissions graph.");
            }

            // Add new permissions for the specified group and database
            Map<String, Object> databasePermissions = new HashMap<>();
            databasePermissions.put("data", new HashMap<String, String>() {{
                put("schemas", "all");
            }});

            // Adding the new permissions to the groups map
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

            // Log the updated permissions graph
            logger.info("Updated Permissions Graph: {}", permissionsGraph);

            // Print the updated permissions graph
            System.out.println("Updated Permissions Graph: " + permissionsGraph);

            // Send the updated permissions graph
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(permissionsGraph, headers);
            response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            logger.info("Response from Metabase API (assign database permissions): " + response.getBody());
        } catch (HttpClientErrorException e) {
            logger.error("Error assigning database permissions for group ID: " + groupId, e);
            logger.error("HTTP Status Code: " + e.getStatusCode());
            logger.error("HTTP Response Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to assign database permissions: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Error assigning database permissions for group ID: " + groupId, e);
            throw new RuntimeException(e);
        }
    }

    private void updateCollectionPermissions(int groupId, int collectionId) {
        logger.info("Updating collection permissions for group ID: {} and collection ID: {}", groupId, collectionId);
        String graphUrl = "http://localhost:3000/api/collection/graph";

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);

        try {
            // Step 1: Get the current collection permissions graph
            
            ResponseEntity<Map> graphResponse = restTemplate.exchange(graphUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> collectionGraph = graphResponse.getBody();

            logger.info("Current collection permissions graph: {}", collectionGraph);

            // Step 2: Modify the collection graph to add collection permissions for the group
            Map<String, Map<String, String>> groups = (Map<String, Map<String, String>>) collectionGraph.get("groups");

            // Check if the group exists in the permissions graph, if not, create it
            groups.computeIfAbsent(String.valueOf(groupId), k -> new HashMap<>());

            Map<String, String> groupPermissions = groups.get(String.valueOf(groupId));
            groupPermissions.put(String.valueOf(collectionId), "write");

            if (groups.containsKey("1")) {
                Map<String, String> group1Permissions = (Map<String, String>) groups.get("1");
                group1Permissions.put(String.valueOf(collectionId), "none"); // Set the permission for the specified collection ID to "none"
            }

            logger.info("Updated collection permissions graph: {}", collectionGraph);

            // Step 3: Send the updated collection graph
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(collectionGraph, headers);
            graphResponse = restTemplate.exchange(graphUrl, HttpMethod.PUT, entity, Map.class);
            logger.info("Response from Metabase API (update collection permissions): " + graphResponse.getBody());
        } catch (Exception e) {
            logger.error("Error updating collection permissions for group ID: {} and collection ID: {}", groupId, collectionId, e);
            throw new RuntimeException(e);
        }
    }
}
