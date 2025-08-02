package org.avni.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.sync.client.AvniApiClient;
import org.avni.sync.error.SyncException;
import org.avni.sync.model.*;
import org.avni.sync.persistence.EntitySyncStatusRepository;
import org.avni.sync.persistence.SyncTelemetryRepository;
import org.avni.sync.util.ProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Main service orchestrating the synchronization process.
 * Handles both upload (push) and download (pull) operations.
 */
public class SyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private final AvniApiClient apiClient;
    private final EntitySyncStatusRepository syncStatusRepository;
    private final SyncTelemetryRepository telemetryRepository;
    private final EntityQueueService entityQueueService;
    private final MediaQueueService mediaQueueService;
    private final EntityPersistenceService entityPersistenceService;
    private final String deviceId;
    
    public SyncService(
            AvniApiClient apiClient,
            EntitySyncStatusRepository syncStatusRepository,
            SyncTelemetryRepository telemetryRepository,
            EntityQueueService entityQueueService,
            MediaQueueService mediaQueueService,
            EntityPersistenceService entityPersistenceService,
            String deviceId) {
        this.apiClient = apiClient;
        this.syncStatusRepository = syncStatusRepository;
        this.telemetryRepository = telemetryRepository;
        this.entityQueueService = entityQueueService;
        this.mediaQueueService = mediaQueueService;
        this.entityPersistenceService = entityPersistenceService;
        this.deviceId = deviceId;
    }
    
    /**
     * Performs complete synchronization including upload and download
     */
    public CompletableFuture<SyncTelemetry> sync(
            List<EntityMetaData> entityMetaDataList,
            SyncTelemetry.SyncSource syncSource,
            ProgressCallback progressCallback,
            Consumer<String> statusMessageCallback,
            Map<String, Object> connectionInfo) {
        
        logger.info("Starting sync with source: {}", syncSource);
        
        SyncTelemetry telemetry = new SyncTelemetry(syncSource, deviceId);
        telemetry.setConnectionInfo(connectionInfo);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                telemetryRepository.save(telemetry);
                
                // Phase 1: Upload local changes
                if (statusMessageCallback != null) {
                    statusMessageCallback.accept("Uploading local changes...");
                }
                uploadLocalChanges(entityMetaDataList, progressCallback);
                telemetry.setEntityPushCompleted(true);
                
                // Phase 2: Upload media files
                if (mediaQueueService.hasMediaToUpload()) {
                    if (statusMessageCallback != null) {
                        statusMessageCallback.accept("Uploading media files...");
                    }
                    uploadMediaFiles(progressCallback);
                    telemetry.setMediaPushCompleted(true);
                }
                
                // Phase 3: Download changes from server (skip for upload-only sync)
                if (syncSource != SyncTelemetry.SyncSource.AUTOMATIC_UPLOAD_ONLY) {
                    if (statusMessageCallback != null) {
                        statusMessageCallback.accept("Downloading changes from server...");
                    }
                    downloadChanges(entityMetaDataList, progressCallback);
                    telemetry.setEntityPullCompleted(true);
                }
                
                telemetry.markSyncCompleted();
                telemetryRepository.save(telemetry);
                
                logger.info("Sync completed successfully in {} ms", telemetry.getSyncDurationMillis());
                return telemetry;
                
            } catch (Exception e) {
                logger.error("Sync failed", e);
                telemetry.markSyncFailed(e.getMessage());
                telemetryRepository.save(telemetry);
                throw new SyncException("Sync failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Uploads all locally queued entities to the server
     */
    private void uploadLocalChanges(List<EntityMetaData> entityMetaDataList, ProgressCallback progressCallback) {
        logger.info("Starting upload of local changes");
        
        List<EntityMetaData> txEntityMetaData = entityMetaDataList.stream()
                .filter(EntityMetaData::isTransactionData)
                .toList();
        
        int totalEntities = 0;
        int processedEntities = 0;
        
        // Calculate total entities for progress tracking
        for (EntityMetaData entityMetaData : txEntityMetaData) {
            totalEntities += entityQueueService.getQueuedItemCount(entityMetaData.getEntityName());
        }
        
        if (progressCallback != null) {
            progressCallback.onTotalItemsCalculated(totalEntities);
        }
        
        for (EntityMetaData entityMetaData : txEntityMetaData) {
            List<EntityQueue> queuedItems = entityQueueService.getQueuedItems(entityMetaData.getEntityName());
            
            for (EntityQueue queuedItem : queuedItems) {
                try {
                    // Get the actual entity from persistence layer
                    JsonNode entityResource = entityPersistenceService.getEntityAsResource(
                            queuedItem.getEntityUuid(), 
                            entityMetaData.getEntityName()
                    );
                    
                    if (entityResource != null) {
                        // Upload to server
                        String entityPath = buildEntityPath(entityMetaData);
                        retrofit2.Response<JsonNode> response = apiClient.postEntity(entityPath, entityResource).execute();
                        
                        if (response.isSuccessful()) {
                            // Remove from queue after successful upload
                            entityQueueService.removeFromQueue(queuedItem.getEntityUuid());
                            processedEntities++;
                            
                            if (progressCallback != null) {
                                progressCallback.onProgress(processedEntities, totalEntities, 
                                    "Uploaded " + entityMetaData.getEntityName());
                            }
                        } else {
                            logger.warn("Failed to upload entity {}: HTTP {}", 
                                queuedItem.getEntityUuid(), response.code());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error uploading entity {}", queuedItem.getEntityUuid(), e);
                    // Continue with next entity rather than failing entire sync
                }
            }
        }
        
        logger.info("Completed upload of {} entities", processedEntities);
    }
    
    /**
     * Uploads all queued media files to the server
     */
    private void uploadMediaFiles(ProgressCallback progressCallback) {
        logger.info("Starting media upload");
        mediaQueueService.uploadAllMedia(progressCallback);
    }
    
    /**
     * Downloads changes from the server based on sync timestamps
     */
    private void downloadChanges(List<EntityMetaData> entityMetaDataList, ProgressCallback progressCallback) {
        logger.info("Starting download of changes from server");
        
        try {
            // Get sync details from server
            List<EntitySyncStatus> currentSyncStatuses = syncStatusRepository.findAll();
            retrofit2.Response<AvniApiClient.SyncDetailsResponse> syncDetailsResponse = 
                apiClient.getSyncDetails(currentSyncStatuses, true, deviceId).execute();
            
            if (!syncDetailsResponse.isSuccessful()) {
                throw new SyncException("Failed to get sync details: HTTP " + syncDetailsResponse.code());
            }
            
            AvniApiClient.SyncDetailsResponse syncDetails = syncDetailsResponse.body();
            if (syncDetails == null || syncDetails.getSyncDetails() == null) {
                logger.warn("No sync details received from server");
                return;
            }
            
            String now = syncDetails.getNow();
            
            // Process reference data first
            List<EntityMetaData> referenceData = entityMetaDataList.stream()
                    .filter(EntityMetaData::isReferenceData)
                    .toList();
            
            downloadEntitiesByType(referenceData, syncDetails.getSyncDetails(), now, progressCallback);
            
            // Then process transaction data
            List<EntityMetaData> transactionData = entityMetaDataList.stream()
                    .filter(EntityMetaData::isTransactionData)
                    .toList();
            
            downloadEntitiesByType(transactionData, syncDetails.getSyncDetails(), now, progressCallback);
            
        } catch (Exception e) {
            logger.error("Error downloading changes", e);
            throw new SyncException("Failed to download changes: " + e.getMessage(), e);
        }
    }
    
    /**
     * Downloads entities of specific types from the server
     */
    private void downloadEntitiesByType(
            List<EntityMetaData> entityMetaDataList,
            List<AvniApiClient.SyncDetail> syncDetails,
            String now,
            ProgressCallback progressCallback) {
        
        for (EntityMetaData entityMetaData : entityMetaDataList) {
            // Find matching sync details for this entity
            List<AvniApiClient.SyncDetail> matchingSyncDetails = syncDetails.stream()
                    .filter(sd -> entityMetaData.getEntityName().equals(sd.getEntityName()))
                    .toList();
            
            for (AvniApiClient.SyncDetail syncDetail : matchingSyncDetails) {
                try {
                    downloadEntityData(entityMetaData, syncDetail, now, progressCallback);
                } catch (Exception e) {
                    logger.error("Error downloading {} with type {}", 
                        entityMetaData.getEntityName(), syncDetail.getEntityTypeUuid(), e);
                    // Continue with next entity type
                }
            }
        }
    }
    
    /**
     * Downloads data for a specific entity type
     */
    private void downloadEntityData(
            EntityMetaData entityMetaData,
            AvniApiClient.SyncDetail syncDetail,
            String now,
            ProgressCallback progressCallback) throws Exception {
        
        String entityPath = buildEntityPath(entityMetaData);
        String lastModifiedDateTime = syncDetail.getLoadedSince();
        
        // Build query parameters
        Map<String, Object> queryParams = Map.of();
        if (entityMetaData.getPrivilegeParam() != null) {
            queryParams = Map.of(entityMetaData.getPrivilegeParam(), syncDetail.getEntityTypeUuid());
        }
        
        int page = 0;
        int pageSize = 100; // Default page size
        int totalProcessed = 0;
        
        do {
            retrofit2.Response<AvniApiClient.PagedResponse<JsonNode>> response = apiClient.getEntities(
                    entityPath, lastModifiedDateTime, now, pageSize, page, queryParams
            ).execute();
            
            if (!response.isSuccessful()) {
                throw new SyncException("Failed to download " + entityMetaData.getEntityName() + 
                    ": HTTP " + response.code());
            }
            
            AvniApiClient.PagedResponse<JsonNode> pagedResponse = response.body();
            if (pagedResponse == null) {
                break;
            }
            
            List<JsonNode> entities = pagedResponse.getEntities(entityMetaData.getResourceName());
            if (entities.isEmpty()) {
                break;
            }
            
            // Persist entities to local database
            entityPersistenceService.persistEntities(entities, entityMetaData);
            totalProcessed += entities.size();
            
            if (progressCallback != null) {
                progressCallback.onProgress(totalProcessed, 
                    (int) (pagedResponse.getPage() != null ? pagedResponse.getPage().getTotalElements() : totalProcessed),
                    "Downloaded " + entityMetaData.getEntityName());
            }
            
            // Update sync status after successful page processing
            if (!entities.isEmpty()) {
                JsonNode lastEntity = entities.get(entities.size() - 1);
                String newLoadedSince = lastEntity.get("lastModifiedDateTime").asText();
                
                EntitySyncStatus syncStatus = EntitySyncStatus.create(
                    entityMetaData.getEntityName(),
                    LocalDateTime.parse(newLoadedSince, ISO_FORMATTER),
                    syncDetail.getUuid(),
                    syncDetail.getEntityTypeUuid()
                );
                syncStatusRepository.save(syncStatus);
            }
            
            page++;
            
            // Check if there are more pages
            if (pagedResponse.getPage() == null || page >= pagedResponse.getPage().getTotalPages()) {
                break;
            }
            
        } while (true);
        
        logger.info("Downloaded {} {} entities", totalProcessed, entityMetaData.getEntityName());
    }
    
    /**
     * Builds the API path for an entity type
     */
    private String buildEntityPath(EntityMetaData entityMetaData) {
        if (entityMetaData.getResourceUrl() != null) {
            return entityMetaData.getResourceUrl();
        }
        
        String resourceName = entityMetaData.getResourceName();
        if (resourceName.equals("entityApprovalStatus")) {
            return resourceName + "es";
        }
        return resourceName + "s";
    }
    
    /**
     * Checks if a full sync was completed more than 12 hours ago
     */
    public boolean wasLastCompletedFullSyncMoreThan12HoursAgo() {
        SyncTelemetry lastFullSync = telemetryRepository.getLatestCompletedFullSync();
        if (lastFullSync == null) {
            return true;
        }
        
        return lastFullSync.getSyncEndTime()
                .plusHours(12)
                .isBefore(LocalDateTime.now());
    }
    
    /**
     * Gets the updated sync source based on timing rules
     */
    public SyncTelemetry.SyncSource getUpdatedSyncSource(SyncTelemetry.SyncSource originalSource) {
        if (originalSource == SyncTelemetry.SyncSource.AUTOMATIC_UPLOAD_ONLY &&
            wasLastCompletedFullSyncMoreThan12HoursAgo()) {
            return SyncTelemetry.SyncSource.AUTOMATIC;
        }
        return originalSource;
    }
}