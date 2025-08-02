package org.avni.camp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.camp.model.EntityMetaData;

import java.util.List;

/**
 * Service for persisting and retrieving entities from local storage.
 * Acts as an abstraction layer over the underlying database implementation.
 */
public interface EntityPersistenceService {
    
    /**
     * Persists a list of entities to local storage
     */
    void persistEntities(List<JsonNode> entities, EntityMetaData entityMetaData);
    
    /**
     * Retrieves an entity as a JSON resource for upload
     */
    JsonNode getEntityAsResource(String entityUuid, String entityName);
    
    /**
     * Replaces a media observation in an entity with the server URL
     */
    void replaceMediaObservation(String entityUuid, String entityName, String fileName, 
                                String serverUrl, String conceptUuid, String targetField);
    
    /**
     * Gets the count of entities of a specific type
     */
    int getEntityCount(String entityName);
    
    /**
     * Deletes entities of a specific type
     */
    void deleteEntitiesByType(String entityName);
    
    /**
     * Deletes a specific entity by UUID
     */
    void deleteEntity(String entityUuid, String entityName);
    
    /**
     * Checks if an entity exists
     */
    boolean entityExists(String entityUuid, String entityName);
    
    /**
     * Gets all entities of a specific type
     */
    List<JsonNode> getEntitiesByType(String entityName);
    
    /**
     * Performs database cleanup operations
     */
    void cleanup();
    
    /**
     * Initializes the database schema
     */
    void initializeSchema();
}