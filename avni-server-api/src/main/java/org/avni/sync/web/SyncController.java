package org.avni.sync.web;

import org.avni.sync.config.SyncConfigurationProperties;
import org.avni.sync.model.EntityMetaData;
import org.avni.sync.model.SyncTelemetry;
import org.avni.sync.service.EntityMetadataService;
import org.avni.sync.service.SyncService;
import org.avni.sync.util.ApiProgressCallback;
import org.avni.sync.util.ProgressCallback;
import org.avni.sync.web.dto.DiagnosticsResponse;
import org.avni.sync.web.dto.SyncResponse;
import org.avni.sync.web.dto.SyncStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);
    
    private final SyncConfigurationProperties config;
    private final SyncService syncService;
    private final EntityMetadataService entityMetadataService;
    
    @Autowired
    public SyncController(SyncConfigurationProperties config, SyncService syncService, 
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
        
        // TODO: Implement actual status retrieval from sync service
        return ResponseEntity.ok(SyncStatusResponse.initialized(
            "Status retrieval not yet fully implemented", 
            null, 
            false
        ));
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
    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("title", "Avni Sync Java API");
        help.put("description", "REST API for Avni synchronization operations");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("POST /api/sync/full", "Run full synchronization (upload + download)");
        endpoints.put("POST /api/sync/upload-only", "Run upload-only sync");
        endpoints.put("GET /api/sync/status", "Show sync status");
        endpoints.put("GET /api/sync/config", "Show configuration");
        endpoints.put("GET /api/sync/diagnostics", "Run diagnostic checks");
        endpoints.put("GET /api/sync/entity-metadata", "Get entity metadata list");
        endpoints.put("GET /api/sync/help", "Show this help");
        
        help.put("endpoints", endpoints);
        
        return ResponseEntity.ok(help);
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
