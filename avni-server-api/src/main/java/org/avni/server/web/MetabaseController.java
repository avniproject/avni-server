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
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                createDatabase(dbUser);

                // Step 2: Create Collection
                createCollection(name);

                // Step 3: Create Permissions Group
                createPermissionsGroup(name);

                // Step 4: Add User to Permissions Group
                // addUserToPermissionsGroup(name, dbUser);

            } catch (Exception e) {
                logger.error("Error setting up Metabase for organisation: " + name, e);
                return ResponseEntity.status(500).body("Failed to setup Metabase for organisation: " + name);
            }
        }

        return ResponseEntity.ok("Metabase setup completed for all organisations.");
    }

    private void createDatabase(String dbUser) {
        String url = "http://localhost:3000/api/database";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> details = new HashMap<>();
        details.put("host", "localhost");
        details.put("port", "5432");  // Add port if required
        details.put("db", "openchs");  // Constant database name
        details.put("user", dbUser);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("engine", "postgres");
        requestBody.put("name", dbUser);  // Or another identifier if preferred
        requestBody.put("details", details);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create database for user: {}", dbUser);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        logger.info("Response from Metabase API (create database): " + response.getBody());
    }

    private void createCollection(String name) {
        String url = "http://localhost:3000/api/collection";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("description", name + " collection");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create collection for name: {}", name);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        logger.info("Response from Metabase API (create collection): " + response.getBody());
    }

    private void createPermissionsGroup(String name) {
        String url = "http://localhost:3000/api/permissions/group";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to create permissions group for name: {}", name);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        logger.info("Response from Metabase API (create permissions group): " + response.getBody());
    }

    private void addUserToPermissionsGroup(String groupName, String dbUser) {
        String url = "http://localhost:3000/api/permissions/membership";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("group_name", groupName);
        requestBody.put("user", dbUser);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to add user to permissions group: {}", groupName);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        logger.info("Response from Metabase API (add user to permissions group): " + response.getBody());
    }
}
