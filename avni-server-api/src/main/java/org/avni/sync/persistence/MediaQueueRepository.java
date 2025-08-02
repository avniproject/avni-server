package org.avni.sync.persistence;

import org.avni.sync.model.MediaQueue;

import java.util.List;

/**
 * Repository interface for managing MediaQueue persistence.
 * Handles CRUD operations for media files queued for upload.
 */
public interface MediaQueueRepository {
    
    /**
     * Saves a media queue item
     */
    void save(MediaQueue mediaQueue);
    
    /**
     * Finds all media queue items
     */
    List<MediaQueue> findAll();
    
    /**
     * Finds media queue items by entity UUID
     */
    List<MediaQueue> findByEntityUuid(String entityUuid);
    
    /**
     * Finds media queue items by entity name
     */
    List<MediaQueue> findByEntityName(String entityName);
    
    /**
     * Finds media queue items by media type
     */
    List<MediaQueue> findByType(String type);
    
    /**
     * Finds media queue item by file name
     */
    MediaQueue findByFileName(String fileName);
    
    /**
     * Finds media queue item by UUID
     */
    MediaQueue findByUuid(String uuid);
    
    /**
     * Deletes a media queue item
     */
    void delete(MediaQueue mediaQueue);
    
    /**
     * Deletes media queue item by UUID
     */
    void deleteByUuid(String uuid);
    
    /**
     * Deletes media queue items by entity UUID
     */
    void deleteByEntityUuid(String entityUuid);
    
    /**
     * Deletes all media queue items for a specific entity name
     */
    void deleteByEntityName(String entityName);
    
    /**
     * Deletes all media queue items
     */
    void deleteAll();
    
    /**
     * Checks if a media queue item exists for given file name
     */
    boolean existsByFileName(String fileName);
    
    /**
     * Checks if a media queue item exists for given entity UUID
     */
    boolean existsByEntityUuid(String entityUuid);
    
    /**
     * Counts media queue items by type
     */
    int countByType(String type);
    
    /**
     * Counts total number of media queue items
     */
    int countTotal();
    
    /**
     * Finds distinct media types in the queue
     */
    List<String> findDistinctTypes();
}