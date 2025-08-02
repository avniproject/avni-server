package org.avni.sync.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for sync status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncStatusResponse {
    
    private boolean initialized;
    private String message;
    private Long lastSyncTime;
    private boolean syncInProgress;
    private String error;
    private Long timestamp;
    
    public SyncStatusResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public static SyncStatusResponse initialized(String message, Long lastSyncTime, boolean syncInProgress) {
        SyncStatusResponse response = new SyncStatusResponse();
        response.initialized = true;
        response.message = message;
        response.lastSyncTime = lastSyncTime;
        response.syncInProgress = syncInProgress;
        return response;
    }
    
    public static SyncStatusResponse notInitialized(String error) {
        SyncStatusResponse response = new SyncStatusResponse();
        response.initialized = false;
        response.error = error;
        return response;
    }
    
    // Getters and setters
    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean initialized) { this.initialized = initialized; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Long getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(Long lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    
    public boolean isSyncInProgress() { return syncInProgress; }
    public void setSyncInProgress(boolean syncInProgress) { this.syncInProgress = syncInProgress; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
