package org.avni.camp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents telemetry data for sync operations.
 * Tracks sync performance, errors, and statistics.
 */
public class SyncTelemetry {
    
    public enum SyncSource {
        MANUAL("manual"),
        AUTOMATIC("automatic"),
        AUTOMATIC_UPLOAD_ONLY("automatic-upload-only");
        
        private final String value;
        
        SyncSource(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static SyncSource fromValue(String value) {
            for (SyncSource source : values()) {
                if (source.value.equals(value)) {
                    return source;
                }
            }
            return MANUAL;
        }
    }
    
    public enum SyncStatus {
        STARTED("STARTED"),
        IN_PROGRESS("IN_PROGRESS"),
        COMPLETED("COMPLETED"),
        FAILED("FAILED");
        
        private final String value;
        
        SyncStatus(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return value;
        }
        
        public static SyncStatus fromValue(String value) {
            for (SyncStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return STARTED;
        }
    }
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("syncStartTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime syncStartTime;
    
    @JsonProperty("syncEndTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime syncEndTime;
    
    @JsonProperty("syncSource")
    private SyncSource syncSource;
    
    @JsonProperty("syncStatus")
    private String syncStatus;
    
    @JsonProperty("syncErrorMessage")
    private String syncErrorMessage;
    
    @JsonProperty("entityPullCompleted")
    private boolean entityPullCompleted = false;
    
    @JsonProperty("entityPushCompleted")
    private boolean entityPushCompleted = false;
    
    @JsonProperty("mediaPushCompleted")
    private boolean mediaPushCompleted = false;
    
    @JsonProperty("appInfo")
    private Map<String, Object> appInfo;
    
    @JsonProperty("connectionInfo")
    private Map<String, Object> connectionInfo;
    
    @JsonProperty("totalEntitiesPulled")
    private int totalEntitiesPulled = 0;
    
    @JsonProperty("totalEntitiesPushed")
    private int totalEntitiesPushed = 0;
    
    @JsonProperty("totalMediaPushed")
    private int totalMediaPushed = 0;
    
    @JsonProperty("deviceId")
    private String deviceId;
    
    @JsonProperty("lastModifiedDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastModifiedDateTime;
    
    @JsonProperty("voided")
    private boolean voided = false;
    
    public SyncTelemetry() {
        this.uuid = UUID.randomUUID().toString();
        this.syncStartTime = LocalDateTime.now();
        this.lastModifiedDateTime = LocalDateTime.now();
        this.syncStatus = "IN_PROGRESS";
    }
    
    public SyncTelemetry(SyncSource syncSource, String deviceId) {
        this();
        this.syncSource = syncSource;
        this.deviceId = deviceId;
    }
    
    public void markSyncCompleted() {
        this.syncEndTime = LocalDateTime.now();
        this.syncStatus = "COMPLETED";
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public void markSyncFailed(String errorMessage) {
        this.syncEndTime = LocalDateTime.now();
        this.syncStatus = "FAILED";
        this.syncErrorMessage = errorMessage;
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public long getSyncDurationMillis() {
        if (syncStartTime != null && syncEndTime != null) {
            return java.time.Duration.between(syncStartTime, syncEndTime).toMillis();
        }
        return 0;
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(syncStatus);
    }
    
    public boolean isFullSync() {
        return SyncSource.MANUAL.equals(syncSource) || SyncSource.AUTOMATIC.equals(syncSource);
    }
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public LocalDateTime getSyncStartTime() {
        return syncStartTime;
    }
    
    public void setSyncStartTime(LocalDateTime syncStartTime) {
        this.syncStartTime = syncStartTime;
    }
    
    public LocalDateTime getSyncEndTime() {
        return syncEndTime;
    }
    
    public void setSyncEndTime(LocalDateTime syncEndTime) {
        this.syncEndTime = syncEndTime;
    }
    
    public SyncSource getSyncSource() {
        return syncSource;
    }
    
    public void setSyncSource(SyncSource syncSource) {
        this.syncSource = syncSource;
    }
    
    public String getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public String getSyncErrorMessage() {
        return syncErrorMessage;
    }
    
    public void setSyncErrorMessage(String syncErrorMessage) {
        this.syncErrorMessage = syncErrorMessage;
    }
    
    public boolean isEntityPullCompleted() {  
        return entityPullCompleted;
    }
    
    public void setEntityPullCompleted(boolean entityPullCompleted) {
        this.entityPullCompleted = entityPullCompleted;
    }
    
    public boolean isEntityPushCompleted() {
        return entityPushCompleted;
    }
    
    public void setEntityPushCompleted(boolean entityPushCompleted) {
        this.entityPushCompleted = entityPushCompleted;
    }
    
    public boolean isMediaPushCompleted() {
        return mediaPushCompleted;
    }
    
    public void setMediaPushCompleted(boolean mediaPushCompleted) {
        this.mediaPushCompleted = mediaPushCompleted;
    }
    
    public Map<String, Object> getAppInfo() {
        return appInfo;
    }
    
    public void setAppInfo(Map<String, Object> appInfo) {
        this.appInfo = appInfo;
    }
    
    public Map<String, Object> getConnectionInfo() {
        return connectionInfo;
    }
    
    public void setConnectionInfo(Map<String, Object> connectionInfo) {
        this.connectionInfo = connectionInfo;
    }
    
    public int getTotalEntitiesPulled() {
        return totalEntitiesPulled;
    }
    
    public void setTotalEntitiesPulled(int totalEntitiesPulled) {
        this.totalEntitiesPulled = totalEntitiesPulled;
    }
    
    public int getTotalEntitiesPushed() {
        return totalEntitiesPushed;
    }
    
    public void setTotalEntitiesPushed(int totalEntitiesPushed) {
        this.totalEntitiesPushed = totalEntitiesPushed;
    }
    
    public int getTotalMediaPushed() {
        return totalMediaPushed;
    }
    
    public void setTotalMediaPushed(int totalMediaPushed) {
        this.totalMediaPushed = totalMediaPushed;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public LocalDateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }
    
    public void setLastModifiedDateTime(LocalDateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }
    
    public boolean isVoided() {
        return voided;
    }
    
    public void setVoided(boolean voided) {
        this.voided = voided;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncTelemetry that = (SyncTelemetry) o;
        return Objects.equals(uuid, that.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
    
    @Override
    public String toString() {
        return "SyncTelemetry{" +
                "uuid='" + uuid + '\'' +
                ", syncStartTime=" + syncStartTime +
                ", syncEndTime=" + syncEndTime +
                ", syncSource=" + syncSource +
                ", syncStatus='" + syncStatus + '\'' +
                ", totalEntitiesPulled=" + totalEntitiesPulled +
                ", totalEntitiesPushed=" + totalEntitiesPushed +
                ", totalMediaPushed=" + totalMediaPushed +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}