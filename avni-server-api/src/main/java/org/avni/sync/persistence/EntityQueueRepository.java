package org.avni.sync.persistence;

import org.avni.sync.model.EntityQueue;

import java.util.List;

/**
 * Repository interface for managing EntityQueue persistence.
 * Handles CRUD operations for entities queued for upload.
 */
public interface EntityQueueRepository {
    
    /**
     * Saves an entity queue item
     */
    void save(EntityQueue entityQueue);
    
    /**
     * Finds all entity queue items
     */
    List<EntityQueue> findAll();
    
    /**
     * Finds entity queue items by entity type
     */
    List<EntityQueue> findByEntityType(String entityType);
    
    /**
     * Finds entity queue item by entity UUID
     */
    EntityQueue findByEntityUuid(String entityUuid);
    
    /**
     * Finds entity queue item by UUID
     */
    EntityQueue findByUuid(String uuid);
    
    /**
     * Finds distinct entity types that have queued items
     */
    List<String> findDistinctEntityTypes();
    
    /**
     * Deletes an entity queue item
     */
    void delete(EntityQueue entityQueue);
    
    /**
     * Deletes entity queue item by entity UUID
     */
    void deleteByEntityUuid(String entityUuid);
    
    /**
     * Deletes all entity queue items for a specific entity type
     */
    void deleteByEntityType(String entityType);
    
    /**
     * Deletes all entity queue items
     */
    void deleteAll();
    
    /**
     * Checks if an entity queue item exists for given entity UUID
     */
    boolean existsByEntityUuid(String entityUuid);
    
    /**
     * Counts entity queue items for a specific entity type
     */
    int countByEntityType(String entityType);
    
    /**
     * Counts total number of entity queue items
     */
    int countTotal();
}