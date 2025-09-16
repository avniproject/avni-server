package org.avni.server.service;

import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Functional tests for bulk subject migration JSON serialization.
 * Tests the core functionality that was causing Spring Batch converter errors.
 */
public class SubjectMigrationServiceTest {

    @Test
    public void jsonSerialization_shouldSerializeAndDeserializeBulkRequestWithAddresses() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L, 2L, 3L));
        
        Map<String, String> destinationAddresses = new HashMap<>();
        destinationAddresses.put("1", "address-1");
        destinationAddresses.put("2", "address-2");
        request.setDestinationAddresses(destinationAddresses);
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("subjectIds"));
        assertTrue(json.contains("destinationAddresses"));
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
        assertEquals(request.getDestinationAddresses(), deserialized.getDestinationAddresses());
    }

    @Test
    public void jsonSerialization_shouldSerializeAndDeserializeBulkRequestWithSyncConcepts() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(4L, 5L));
        
        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put("concept-1", "value-1");
        destinationSyncConcepts.put("concept-2", "value-2");
        request.setDestinationSyncConcepts(destinationSyncConcepts);
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("subjectIds"));
        assertTrue(json.contains("destinationSyncConcepts"));
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
        assertEquals(request.getDestinationSyncConcepts(), deserialized.getDestinationSyncConcepts());
    }

    @Test
    public void jsonSerialization_shouldHandleEmptyCollections() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList());
        request.setDestinationAddresses(new HashMap<>());
        request.setDestinationSyncConcepts(new HashMap<>());
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertNotNull(deserialized.getSubjectIds());
        assertTrue(deserialized.getSubjectIds().isEmpty());
    }

    @Test
    public void jsonSerialization_shouldHandleNullValues() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L));
        // Leave other fields null
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
    }
}
