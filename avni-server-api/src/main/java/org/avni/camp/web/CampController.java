package org.avni.camp.web;

import org.avni.camp.config.CampConfigurationProperties;
import org.avni.camp.model.EntityMetaData;
import org.avni.camp.model.SyncTelemetry;
import org.avni.camp.service.EntityMetadataService;
import org.avni.camp.service.SyncService;
import org.avni.camp.util.ApiProgressCallback;
import org.avni.camp.util.ProgressCallback;
import org.avni.camp.web.dto.DiagnosticsResponse;
import org.avni.camp.web.dto.SyncResponse;
import org.avni.camp.web.dto.SyncStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/camp")
public class CampController {
    
    private static final Logger logger = LoggerFactory.getLogger(CampController.class);
    
    private final CampConfigurationProperties config;
    private final SyncService syncService;
    private final EntityMetadataService entityMetadataService;
    
    @Autowired
    public CampController(CampConfigurationProperties config, SyncService syncService, 
                         EntityMetadataService entityMetadataService) {
        this.config = config;
        this.syncService = syncService;
        this.entityMetadataService = entityMetadataService;
    }
    
    /**
     * Runs full synchronization (upload + download)
     */
    @PostMapping("/full")
    public ResponseEntity<SyncResponse> runFullSync(@RequestParam(defaultValue = "false") boolean background) {
        logger.info("Starting full sync, background: {}", background);
        
        SyncTelemetry.SyncSource syncSource = background ? 
            SyncTelemetry.SyncSource.AUTOMATIC : SyncTelemetry.SyncSource.MANUAL;
        
        return performSync(syncSource);
    }
    
    /**
     * Runs upload-only synchronization
     */
    @PostMapping("/upload-only")
    public ResponseEntity<SyncResponse> runUploadOnlySync() {
        logger.info("Starting upload-only sync");
        return performSync(SyncTelemetry.SyncSource.AUTOMATIC_UPLOAD_ONLY);
    }
    
    /**
     * Gets current sync status
     */
    @GetMapping("/status")
    public ResponseEntity<SyncStatusResponse> getSyncStatus() {
        logger.info("Retrieving sync status");
        
        if (syncService == null) {
            return ResponseEntity.ok(SyncStatusResponse.notInitialized("Sync service not initialized"));
        }
        
        try {
            // Get the latest sync telemetry
            SyncTelemetry latestSync = getLatestSyncTelemetry();
            
            if (latestSync == null) {
                return ResponseEntity.ok(SyncStatusResponse.initialized(
                    "No sync performed yet", 
                    null, 
                    false
                ));
            }
            
            boolean isRunning = "STARTED".equals(latestSync.getSyncStatus()) || "IN_PROGRESS".equals(latestSync.getSyncStatus());
            
            Long lastSyncTime = null;
            if (latestSync.getSyncEndTime() != null) {
                lastSyncTime = latestSync.getSyncEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            
            return ResponseEntity.ok(SyncStatusResponse.initialized(
                getSyncStatusMessage(latestSync),
                lastSyncTime,
                isRunning
            ));
            
        } catch (Exception e) {
            logger.error("Error retrieving sync status", e);
            return ResponseEntity.ok(SyncStatusResponse.error("Error retrieving sync status: " + e.getMessage()));
        }
    }
    
    /**
     * Gets current configuration
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        logger.info("Retrieving sync configuration");
        
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("serverUrl", config.getServerUrl());
        configMap.put("deviceId", config.getDeviceId());
        configMap.put("databaseUrl", config.getDatabaseUrl());
        configMap.put("mediaDirectory", config.getMediaBaseDirectory());
        configMap.put("autoSyncEnabled", config.isAutoSyncEnabled());
        configMap.put("backgroundSyncIntervalMinutes", config.getBackgroundSyncInterval().toMinutes());
        configMap.put("pageSize", config.getDefaultPageSize());
        configMap.put("maxRetryAttempts", config.getMaxRetryAttempts());
        
        return ResponseEntity.ok(configMap);
    }
    
    /**
     * Runs diagnostic checks
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<DiagnosticsResponse> runDiagnostics() {
        logger.info("Running sync diagnostics");
        
        // Check server connectivity
        DiagnosticsResponse.DiagnosticCheck serverCheck = 
            DiagnosticsResponse.DiagnosticCheck.notImplemented("Server Connectivity", "Not implemented in demo");
        
        // Check database connectivity
        DiagnosticsResponse.DiagnosticCheck databaseCheck = 
            DiagnosticsResponse.DiagnosticCheck.notImplemented("Database Connectivity", "Not implemented in demo");
        
        // Check media directory
        File mediaDir = new File(config.getMediaBaseDirectory());
        DiagnosticsResponse.DiagnosticCheck mediaCheck;
        if (mediaDir.exists() && mediaDir.isDirectory()) {
            mediaCheck = DiagnosticsResponse.DiagnosticCheck.ok("Media Directory", 
                "Media directory exists: " + mediaDir.getAbsolutePath());
        } else {
            mediaCheck = DiagnosticsResponse.DiagnosticCheck.error("Media Directory", 
                "Media directory does not exist: " + mediaDir.getAbsolutePath());
        }
        
        // Check configuration
        DiagnosticsResponse.DiagnosticCheck configCheck;
        try {
            config.validate();
            configCheck = DiagnosticsResponse.DiagnosticCheck.ok("Configuration", "Configuration is valid");
        } catch (Exception e) {
            configCheck = DiagnosticsResponse.DiagnosticCheck.error("Configuration", 
                "Configuration error: " + e.getMessage());
        }
        
        return ResponseEntity.ok(new DiagnosticsResponse(
            List.of(serverCheck, databaseCheck, mediaCheck, configCheck)
        ));
    }
    
    /**
     * Gets entity metadata list
     */
    @GetMapping("/entity-metadata")
    public ResponseEntity<List<EntityMetaData>> getEntityMetadata() {
        logger.info("Retrieving entity metadata");
        
        List<EntityMetaData> entityMetaDataList = entityMetadataService.getEntityMetaDataList();
        return ResponseEntity.ok(entityMetaDataList);
    }
    
    /**
     * Gets help information
     */
    @GetMapping("/quick-help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("title", "Avni Sync Java API");
        help.put("description", "REST API for Avni synchronization operations");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("POST /api/camp/full", "Run full synchronization (upload + download)");
        endpoints.put("POST /api/camp/upload-only", "Run upload-only sync");
        endpoints.put("GET /api/camp/status", "Show sync status");
        endpoints.put("GET /api/camp/config", "Show configuration");
        endpoints.put("GET /api/camp/diagnostics", "Run diagnostic checks");
        endpoints.put("GET /api/camp/entity-metadata", "Get entity metadata list");
        endpoints.put("GET /api/camp/quick-help", "Show this help");
        
        help.put("endpoints", endpoints);
        
        return ResponseEntity.ok(help);
    }
    
    /**
     * Gets sync history/logs
     */
    @GetMapping("/history")
    public ResponseEntity<List<SyncTelemetry>> getSyncHistory(@RequestParam(defaultValue = "10") int limit) {
        logger.info("Retrieving sync history with limit: {}", limit);
        
        try {
            // This would be implemented with the telemetry repository
            // For now, return empty list as placeholder
            return ResponseEntity.ok(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error retrieving sync history", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    /**
     * Gets detailed sync statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSyncStatistics() {
        logger.info("Retrieving sync statistics");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Mock statistics - in real implementation, these would come from repositories
            stats.put("totalSyncs", 0);
            stats.put("successfulSyncs", 0);
            stats.put("failedSyncs", 0);
            stats.put("lastSyncTime", null);
            stats.put("averageSyncDuration", 0);
            stats.put("totalEntitiesSynced", 0);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error retrieving sync statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Cancels current sync operation
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelSync() {
        logger.info("Cancelling current sync operation");
        
        try {
            // TODO: Implement sync cancellation logic
            Map<String, String> response = new HashMap<>();
            response.put("message", "Sync cancellation not yet implemented");
            response.put("status", "warning");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling sync", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error cancelling sync: " + e.getMessage());
            errorResponse.put("status", "error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Gets the latest sync telemetry record
     */
    private SyncTelemetry getLatestSyncTelemetry() {
        // TODO: Implement with telemetry repository
        // For now, return null as placeholder
        return null;
    }
    
    /**
     * Generates a human-readable status message for sync telemetry
     */
    private String getSyncStatusMessage(SyncTelemetry syncTelemetry) {
        if (syncTelemetry == null) {
            return "No sync information available";
        }
        
        switch (syncTelemetry.getSyncStatus()) {
            case "STARTED":
            case "IN_PROGRESS":
                return "Sync in progress...";
            case "COMPLETED":
                return String.format("Last sync completed successfully. " +
                    "Pushed: %d entities, %d media files. Pulled: %d entities.",
                    syncTelemetry.getTotalEntitiesPushed(),
                    syncTelemetry.getTotalMediaPushed(),
                    syncTelemetry.getTotalEntitiesPulled());
            case "FAILED":
                return "Last sync failed: " + 
                    (syncTelemetry.getSyncErrorMessage() != null ? 
                        syncTelemetry.getSyncErrorMessage() : "Unknown error");
            default:
                return "Sync status unknown";
        }
    }
    
    /**
     * Performs synchronization with the specified source
     */
    private ResponseEntity<SyncResponse> performSync(SyncTelemetry.SyncSource syncSource) {
        if (syncService == null) {
            return ResponseEntity.badRequest().body(
                SyncResponse.error("Sync service not initialized. This is a demo implementation.")
            );
        }
        
        try {
            // Create progress callback for API (could be enhanced to support WebSocket for real-time updates)
            ProgressCallback progressCallback = new ApiProgressCallback();
            
            // Create status message callback
            java.util.function.Consumer<String> statusCallback = message -> 
                logger.info("Sync status: {}", message);
            
            // Get entity metadata
            List<EntityMetaData> entityMetaDataList = entityMetadataService.getEntityMetaDataList();
            
            // Connection info
            Map<String, Object> connectionInfo = Map.of(
                "type", "api",
                "connected", true,
                "timestamp", System.currentTimeMillis()
            );
            
            CompletableFuture<SyncTelemetry> syncFuture = syncService.sync(
                entityMetaDataList,
                syncSource,
                progressCallback,
                statusCallback,
                connectionInfo
            );
            
            SyncTelemetry result = syncFuture.get();
            
            logger.info("Sync completed successfully. Duration: {} ms", result.getSyncDurationMillis());
            
            return ResponseEntity.ok(SyncResponse.success(
                syncSource.toString(),
                result.getSyncDurationMillis(),
                result.getTotalEntitiesPulled(),
                result.getTotalEntitiesPushed(),
                result.getTotalMediaPushed()
            ));
            
        } catch (Exception e) {
            logger.error("Sync failed", e);
            return ResponseEntity.internalServerError().body(SyncResponse.error(e.getMessage()));
        }
    }
    

    

}
