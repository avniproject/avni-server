package org.avni.sync.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller providing API documentation and help for sync endpoints
 */
@RestController
@RequestMapping("/api/sync")
public class SyncDocumentationController {
    
    /**
     * Provides comprehensive API documentation
     */
    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> getApiDocumentation() {
        Map<String, Object> docs = new HashMap<>();
        
        docs.put("title", "Avni Sync Java API");
        docs.put("version", "1.0.0");
        docs.put("description", "REST API for Avni offline-first synchronization functionality");
        
        Map<String, Object> endpoints = new HashMap<>();
        
        // Sync operations
        Map<String, Object> fullSync = new HashMap<>();
        fullSync.put("method", "POST");
        fullSync.put("path", "/api/sync/full");
        fullSync.put("description", "Run full synchronization (upload + download)");
        fullSync.put("parameters", Map.of("background", "boolean (optional) - Run as background sync"));
        fullSync.put("response", "SyncResponse with sync results");
        endpoints.put("fullSync", fullSync);
        
        Map<String, Object> uploadSync = new HashMap<>();
        uploadSync.put("method", "POST");
        uploadSync.put("path", "/api/sync/upload-only");
        uploadSync.put("description", "Run upload-only synchronization");
        uploadSync.put("response", "SyncResponse with sync results");
        endpoints.put("uploadOnlySync", uploadSync);
        
        // Status and information
        Map<String, Object> status = new HashMap<>();
        status.put("method", "GET");
        status.put("path", "/api/sync/status");
        status.put("description", "Get current sync status");
        status.put("response", "SyncStatusResponse with current status");
        endpoints.put("status", status);
        
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("path", "/api/sync/config");
        config.put("description", "Get current sync configuration");
        config.put("response", "Configuration object");
        endpoints.put("config", config);
        
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("method", "GET");
        diagnostics.put("path", "/api/sync/diagnostics");
        diagnostics.put("description", "Run diagnostic checks");
        diagnostics.put("response", "DiagnosticsResponse with check results");
        endpoints.put("diagnostics", diagnostics);
        
        Map<String, Object> entityMetadata = new HashMap<>();
        entityMetadata.put("method", "GET");
        entityMetadata.put("path", "/api/sync/entity-metadata");
        entityMetadata.put("description", "Get entity metadata list");
        entityMetadata.put("response", "List of EntityMetaData objects");
        endpoints.put("entityMetadata", entityMetadata);
        
        docs.put("endpoints", endpoints);
        
        // Response schemas
        Map<String, Object> schemas = new HashMap<>();
        
        Map<String, Object> syncResponseSchema = new HashMap<>();
        syncResponseSchema.put("success", "boolean - Operation success status");
        syncResponseSchema.put("syncSource", "string - Source of sync operation");
        syncResponseSchema.put("durationMillis", "number - Sync duration in milliseconds");
        syncResponseSchema.put("entitiesPulled", "number - Number of entities downloaded");
        syncResponseSchema.put("entitiesPushed", "number - Number of entities uploaded");
        syncResponseSchema.put("mediaPushed", "number - Number of media files uploaded");
        syncResponseSchema.put("error", "string - Error message (if failed)");
        syncResponseSchema.put("timestamp", "number - Response timestamp");
        schemas.put("SyncResponse", syncResponseSchema);
        
        Map<String, Object> statusResponseSchema = new HashMap<>();
        statusResponseSchema.put("initialized", "boolean - Service initialization status");
        statusResponseSchema.put("message", "string - Status message");
        statusResponseSchema.put("lastSyncTime", "number - Last sync timestamp");
        statusResponseSchema.put("syncInProgress", "boolean - Whether sync is currently running");
        statusResponseSchema.put("error", "string - Error message (if any)");
        statusResponseSchema.put("timestamp", "number - Response timestamp");
        schemas.put("SyncStatusResponse", statusResponseSchema);
        
        docs.put("schemas", schemas);
        
        return ResponseEntity.ok(docs);
    }
    
    /**
     * Provides quick help information
     */
    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("title", "Avni Sync Java API - Quick Help");
        help.put("description", "REST API for Avni synchronization operations");
        
        Map<String, String> quickCommands = new HashMap<>();
        quickCommands.put("POST /api/sync/full", "Run full sync");
        quickCommands.put("POST /api/sync/upload-only", "Upload only");
        quickCommands.put("GET /api/sync/status", "Check status");
        quickCommands.put("GET /api/sync/config", "View config");
        quickCommands.put("GET /api/sync/diagnostics", "Run diagnostics");
        quickCommands.put("GET /api/sync/docs", "Full documentation");
        
        help.put("endpoints", quickCommands);
        help.put("note", "This API replaces the command-line interface of AvniSyncApplication");
        
        return ResponseEntity.ok(help);
    }
}
