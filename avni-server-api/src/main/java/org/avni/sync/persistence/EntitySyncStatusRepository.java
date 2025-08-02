package org.avni.sync.persistence;

import org.avni.sync.model.EntitySyncStatus;

import java.util.List;

/**
 * Repository interface for managing EntitySyncStatus persistence.
 * Handles CRUD operations for entity synchronization status tracking.
 */
public interface EntitySyncStatusRepository {
    
    /**
     * Saves or updates an entity sync status
     */
    void save(EntitySyncStatus entitySyncStatus);
    
    /**
     * Finds all entity sync statuses
     */
    List<EntitySyncStatus> findAll();
    
    /**
     * Finds entity sync status by entity name and type UUID
     */
    EntitySyncStatus findByEntityNameAndTypeUuid(String entityName, String entityTypeUuid);
    
    /**
     * Finds entity sync status by entity name (for entities without type UUID)
     */
    EntitySyncStatus findByEntityName(String entityName);
    
    /**
     * Finds all sync statuses for a specific entity name
     */
    List<EntitySyncStatus> findAllByEntityName(String entityName);
    
    /**
     * Finds distinct entity names that have sync status
     */
    List<String> findDistinctEntityNames();
    
    /**
     * Finds entity sync status by UUID
     */
    EntitySyncStatus findByUuid(String uuid);
    
    /**
     * Deletes an entity sync status
     */
    void delete(EntitySyncStatus entitySyncStatus);
    
    /**
     * Deletes all entity sync statuses for a specific entity name and type UUIDs
     */
    void deleteByEntityNameAndTypeUuids(String entityName, List<String> entityTypeUuids);
    
    /**
     * Deletes all entity sync statuses
     */
    void deleteAll();
    
    /**
     * Checks if an entity sync status exists for given entity name and type UUID
     */
    boolean existsByEntityNameAndTypeUuid(String entityName, String entityTypeUuid);
    
    /**
     * Counts total number of entity sync statuses
     */
    int countTotal();
    
    /**
     * Initializes sync status for entities that don't have one yet
     */
    void initializeMissingSyncStatuses(List<String> entityNames);
}