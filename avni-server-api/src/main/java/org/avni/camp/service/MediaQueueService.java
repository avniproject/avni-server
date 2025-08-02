package org.avni.camp.service;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.avni.camp.client.AvniApiClient;
import org.avni.camp.error.SyncException;
import org.avni.camp.model.MediaQueue;
import org.avni.camp.persistence.MediaQueueRepository;
import org.avni.camp.util.ProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing media files queued for upload to the server.
 * Handles adding, uploading, and removing media files from the upload queue.
 */
public class MediaQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(MediaQueueService.class);
    private static final int PARALLEL_UPLOAD_COUNT = 1; // Conservative upload parallelism
    private static final int UPLOAD_TIMEOUT_MINUTES = 30;
    
    private final MediaQueueRepository repository;
    private final AvniApiClient apiClient;
    private final EntityPersistenceService entityPersistenceService;
    private final String mediaBaseDirectory;
    
    public MediaQueueService(
            MediaQueueRepository repository,
            AvniApiClient apiClient,
            EntityPersistenceService entityPersistenceService,
            String mediaBaseDirectory) {
        this.repository = repository;
        this.apiClient = apiClient;
        this.entityPersistenceService = entityPersistenceService;
        this.mediaBaseDirectory = mediaBaseDirectory;
    }
    
    /**
     * Adds a media file to the upload queue
     */
    public void addToQueue(String entityUuid, String entityName, String fileName, String mediaType, String entityTargetField, String conceptUuid) {
        try {
            // Prevent duplicate queue entries
            if (repository.existsByFileName(fileName)) {
                logger.debug("Media file {} already in queue, skipping", fileName);
                return;
            }
            
            // Check if file already uploaded (starts with http)
            if (fileName.startsWith("http")) {
                logger.debug("Media file {} already uploaded, skipping queue", fileName);
                return;
            }
            
            MediaQueue mediaQueue = MediaQueue.create(entityUuid, entityName, fileName, mediaType, entityTargetField, conceptUuid);
            repository.save(mediaQueue);
            
            logger.debug("Added media file {} to queue for entity {}", fileName, entityUuid);
        } catch (Exception e) {
            logger.error("Failed to add media file {} to queue", fileName, e);
            throw new RuntimeException("Failed to add media file to queue", e);
        }
    }
    
    /**
     * Uploads all queued media files to the server
     */
    public void uploadAllMedia(ProgressCallback progressCallback) {
        List<MediaQueue> queuedItems = repository.findAll();
        if (queuedItems.isEmpty()) {
            logger.info("No media files to upload");
            return;
        }
        
        logger.info("Starting upload of {} media files", queuedItems.size());
        
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_UPLOAD_COUNT);
        int totalItems = queuedItems.size();
        int[] processedCount = {0}; // Use array for thread-safe increment
        
        try {
            for (MediaQueue mediaItem : queuedItems) {
                executor.submit(() -> {
                    try {
                        uploadMediaItem(mediaItem);
                        
                        synchronized (processedCount) {
                            processedCount[0]++;
                            if (progressCallback != null) {
                                progressCallback.onProgress(processedCount[0], totalItems, 
                                    "Uploaded " + mediaItem.getFileName());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to upload media file {}", mediaItem.getFileName(), e);
                        // Continue with other uploads rather than failing all
                    }
                });
            }
            
            executor.shutdown();
            if (!executor.awaitTermination(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                logger.warn("Media upload timeout reached, some uploads may not have completed");
                executor.shutdownNow();
            }
            
            logger.info("Completed media upload, processed {} files", processedCount[0]);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new SyncException("Media upload interrupted", e);
        }
    }
    
    /**
     * Uploads a single media item to the server
     */
    private void uploadMediaItem(MediaQueue mediaItem) throws Exception {
        // Check if file exists locally
        Path filePath = getAbsoluteFilePath(mediaItem);
        if (!Files.exists(filePath)) {
            logger.warn("Media file {} does not exist locally, removing from queue", mediaItem.getFileName());
            // Don't remove from queue immediately - this helps highlight missing media to users
            return;
        }
        
        try {
            // Get upload URL from server
            retrofit2.Response<String> uploadUrlResponse = apiClient.getMediaUploadUrl(mediaItem.getFileName()).execute();
            if (!uploadUrlResponse.isSuccessful()) {
                throw new SyncException("Failed to get upload URL for " + mediaItem.getFileName() + 
                    ": HTTP " + uploadUrlResponse.code());
            }
            
            String uploadUrl = uploadUrlResponse.body();
            if (uploadUrl == null || uploadUrl.trim().isEmpty()) {
                throw new SyncException("Empty upload URL received for " + mediaItem.getFileName());
            }
            
            // Upload file to S3
            uploadFileToUrl(filePath, uploadUrl);
            
            // Update entity with uploaded URL
            replaceEntityMediaReference(mediaItem, uploadUrl);
            
            // Remove from queue after successful upload
            repository.delete(mediaItem);
            
            logger.debug("Successfully uploaded media file {}", mediaItem.getFileName());
            
        } catch (Exception e) {
            logger.error("Error uploading media file {}", mediaItem.getFileName(), e);
            throw e;
        }
    }
    
    /**
     * Uploads a file to the provided URL
     */
    private void uploadFileToUrl(Path filePath, String uploadUrl) throws Exception {
        try {
            File file = filePath.toFile();
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            RequestBody requestBody = RequestBody.create(file, MediaType.parse(contentType));
            
            retrofit2.Response<Void> uploadResponse = apiClient.uploadMedia(uploadUrl, requestBody, contentType).execute();
            
            if (!uploadResponse.isSuccessful()) {
                throw new SyncException("Failed to upload file " + filePath.getFileName() + 
                    ": HTTP " + uploadResponse.code());
            }
            
        } catch (IOException e) {
            throw new SyncException("IO error uploading file " + filePath.getFileName(), e);
        }
    }
    
    /**
     * Updates the entity reference from local file path to server URL
     */
    private void replaceEntityMediaReference(MediaQueue mediaItem, String uploadUrl) {
        try {
            // Extract clean URL without query parameters
            String canonicalUrl = uploadUrl.contains("?") ? uploadUrl.substring(0, uploadUrl.indexOf("?")) : uploadUrl;
            
            entityPersistenceService.replaceMediaObservation(
                mediaItem.getEntityUuid(),
                mediaItem.getEntityName(),
                mediaItem.getFileName(),
                canonicalUrl,
                mediaItem.getConceptUuid(),
                mediaItem.getEntityTargetField()
            );
            
            logger.debug("Updated entity {} media reference from {} to {}", 
                mediaItem.getEntityUuid(), mediaItem.getFileName(), canonicalUrl);
                
        } catch (Exception e) {
            logger.error("Failed to update entity media reference for {}", mediaItem.getEntityUuid(), e);
            throw new RuntimeException("Failed to update entity media reference", e);
        }
    }
    
    /**
     * Gets the absolute file path for a media item
     */
    private Path getAbsoluteFilePath(MediaQueue mediaItem) {
        String subdirectory = getSubdirectoryByType(mediaItem.getType());
        return Paths.get(mediaBaseDirectory, subdirectory, mediaItem.getFileName());
    }
    
    /**
     * Maps media type to storage subdirectory
     */
    private String getSubdirectoryByType(String mediaType) {
        return switch (mediaType.toLowerCase()) {
            case "image", "imagev2" -> "images";
            case "profile-pics" -> "profile-pics";
            case "video" -> "videos";
            case "audio" -> "audio";
            case "file" -> "files";
            default -> "media";
        };
    }
    
    /**
     * Checks if there are media files to upload
     */
    public boolean hasMediaToUpload() {
        try {
            return repository.countTotal() > 0;
        } catch (Exception e) {
            logger.error("Failed to check if media upload required", e);
            return false;
        }
    }
    
    /**
     * Gets all queued media items
     */
    public List<MediaQueue> getAllQueuedMedia() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            logger.error("Failed to get all queued media", e);
            throw new RuntimeException("Failed to get all queued media", e);
        }
    }
    
    /**
     * Gets count of queued media items
     */
    public int getQueuedMediaCount() {
        try {
            return repository.countTotal();
        } catch (Exception e) {
            logger.error("Failed to get queued media count", e);
            return 0;
        }
    }
    
    /**
     * Removes a media item from the queue
     */
    public void removeFromQueue(String mediaItemUuid) {
        try {
            MediaQueue mediaItem = repository.findByUuid(mediaItemUuid);
            if (mediaItem != null) {
                repository.delete(mediaItem);
                logger.debug("Removed media item {} from queue", mediaItemUuid);
            }
        } catch (Exception e) {
            logger.error("Failed to remove media item {} from queue", mediaItemUuid, e);
            throw new RuntimeException("Failed to remove media item from queue", e);
        }
    }
    
    /**
     * Clears all queued media items
     */
    public void clearQueue() {
        try {
            repository.deleteAll();
            logger.info("Cleared all queued media items");
        } catch (Exception e) {
            logger.error("Failed to clear media queue", e);
            throw new RuntimeException("Failed to clear media queue", e);
        }
    }
}