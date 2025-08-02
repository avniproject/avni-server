package org.avni.camp.service;

import org.avni.camp.model.EntityQueue;
import org.avni.camp.persistence.EntityQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for managing entities queued for upload to the server.
 * Handles adding, retrieving, and removing entities from the upload queue.
 */
public class EntityQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(EntityQueueService.class);
    
    private final EntityQueueRepository repository;
    
    public EntityQueueService(EntityQueueRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Adds an entity to the upload queue
     */
    public void addToQueue(String entityUuid, String entityType) {
        try {
            // Check if entity is already in queue
            if (repository.existsByEntityUuid(entityUuid)) {
                logger.debug("Entity {} already in queue, skipping", entityUuid);
                return;
            }
            
            EntityQueue queueItem = EntityQueue.create(entityUuid, entityType);
            repository.save(queueItem);
            
            logger.debug("Added entity {} of type {} to queue", entityUuid, entityType);
        } catch (Exception e) {
            logger.error("Failed to add entity {} to queue", entityUuid, e);
            throw new RuntimeException("Failed to add entity to queue", e);
        }
    }
    
    /**
     * Gets all queued items for a specific entity type
     */
    public List<EntityQueue> getQueuedItems(String entityType) {
        try {
            return repository.findByEntityType(entityType);
        } catch (Exception e) {
            logger.error("Failed to get queued items for entity type {}", entityType, e);
            throw new RuntimeException("Failed to get queued items", e);
        }
    }
    
    /**
     * Gets all queued items across all entity types
     */
    public List<EntityQueue> getAllQueuedItems() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            logger.error("Failed to get all queued items", e);
            throw new RuntimeException("Failed to get all queued items", e);
        }
    }
    
    /**
     * Gets the count of queued items for a specific entity type
     */
    public int getQueuedItemCount(String entityType) {
        try {
            return repository.countByEntityType(entityType);
        } catch (Exception e) {
            logger.error("Failed to get queued item count for entity type {}", entityType, e);
            return 0;
        }
    }
    
    /**
     * Gets the total count of all queued items
     */
    public int getTotalQueuedItemCount() {
        try {
            return repository.countTotal();
        } catch (Exception e) {
            logger.error("Failed to get total queued item count", e);
            return 0;
        }
    }
    
    /**
     * Removes an entity from the upload queue
     */
    public void removeFromQueue(String entityUuid) {
        try {
            EntityQueue queueItem = repository.findByEntityUuid(entityUuid);
            if (queueItem != null) {
                repository.delete(queueItem);
                logger.debug("Removed entity {} from queue", entityUuid);
            } else {
                logger.warn("Entity {} not found in queue for removal", entityUuid);
            }
        } catch (Exception e) {
            logger.error("Failed to remove entity {} from queue", entityUuid, e);
            throw new RuntimeException("Failed to remove entity from queue", e);
        }
    }
    
    /**
     * Removes all queued items for a specific entity type
     */
    public void clearQueueForEntityType(String entityType) {
        try {
            List<EntityQueue> queuedItems = repository.findByEntityType(entityType);
            for (EntityQueue item : queuedItems) {
                repository.delete(item);
            }
            logger.info("Cleared {} queued items for entity type {}", queuedItems.size(), entityType);
        } catch (Exception e) {
            logger.error("Failed to clear queue for entity type {}", entityType, e);
            throw new RuntimeException("Failed to clear queue for entity type", e);
        }
    }
    
    /**
     * Clears all queued items
     */
    public void clearAllQueues() {
        try {
            repository.deleteAll();
            logger.info("Cleared all queued items");
        } catch (Exception e) {
            logger.error("Failed to clear all queues", e);
            throw new RuntimeException("Failed to clear all queues", e);
        }
    }
    
    /**
     * Gets distinct entity types that have queued items
     */
    public List<String> getEntityTypesWithQueuedItems() {
        try {
            return repository.findDistinctEntityTypes();
        } catch (Exception e) {
            logger.error("Failed to get entity types with queued items", e);
            throw new RuntimeException("Failed to get entity types with queued items", e);
        }
    }
    
    /**
     * Checks if there are any items in the queue
     */
    public boolean hasQueuedItems() {
        try {
            return repository.countTotal() > 0;
        } catch (Exception e) {
            logger.error("Failed to check if queue has items", e);
            return false;
        }
    }
    
    /**
     * Checks if a specific entity is in the queue
     */
    public boolean isEntityQueued(String entityUuid) {
        try {
            return repository.existsByEntityUuid(entityUuid);
        } catch (Exception e) {
            logger.error("Failed to check if entity {} is queued", entityUuid, e);
            return false;
        }
    }
}