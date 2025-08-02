package org.avni.camp.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for sync operations
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncResponse {
    
    private boolean success;
    private String syncSource;
    private Long durationMillis;
    private Integer entitiesPulled;
    private Integer entitiesPushed;
    private Integer mediaPushed;
    private String error;
    private Long timestamp;
    
    public SyncResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public static SyncResponse success(String syncSource, long durationMillis, 
                                     int entitiesPulled, int entitiesPushed, int mediaPushed) {
        SyncResponse response = new SyncResponse();
        response.success = true;
        response.syncSource = syncSource;
        response.durationMillis = durationMillis;
        response.entitiesPulled = entitiesPulled;
        response.entitiesPushed = entitiesPushed;
        response.mediaPushed = mediaPushed;
        return response;
    }
    
    public static SyncResponse error(String error) {
        SyncResponse response = new SyncResponse();
        response.success = false;
        response.error = error;
        return response;
    }
    
    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getSyncSource() { return syncSource; }
    public void setSyncSource(String syncSource) { this.syncSource = syncSource; }
    
    public Long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
    
    public Integer getEntitiesPulled() { return entitiesPulled; }
    public void setEntitiesPulled(Integer entitiesPulled) { this.entitiesPulled = entitiesPulled; }
    
    public Integer getEntitiesPushed() { return entitiesPushed; }
    public void setEntitiesPushed(Integer entitiesPushed) { this.entitiesPushed = entitiesPushed; }
    
    public Integer getMediaPushed() { return mediaPushed; }
    public void setMediaPushed(Integer mediaPushed) { this.mediaPushed = mediaPushed; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
