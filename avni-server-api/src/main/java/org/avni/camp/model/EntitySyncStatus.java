package org.avni.camp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the synchronization status of an entity type.
 * Tracks when data was last loaded from the server and entity type information.
 */
public class EntitySyncStatus {
    
    public static final LocalDateTime REALLY_OLD_DATE = LocalDateTime.of(1900, 1, 1, 0, 0);
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("entityName")
    private String entityName;
    
    @JsonProperty("entityTypeUuid")
    private String entityTypeUuid;
    
    @JsonProperty("loadedSince")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime loadedSince;
    
    @JsonProperty("lastModifiedDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastModifiedDateTime;
    
    @JsonProperty("voided")
    private boolean voided = false;
    
    public EntitySyncStatus() {
        this.uuid = UUID.randomUUID().toString();
        this.loadedSince = REALLY_OLD_DATE;
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public EntitySyncStatus(String entityName, LocalDateTime loadedSince, String uuid, String entityTypeUuid) {
        this.entityName = entityName;
        this.loadedSince = loadedSince != null ? loadedSince : REALLY_OLD_DATE;
        this.uuid = uuid != null ? uuid : UUID.randomUUID().toString();
        this.entityTypeUuid = entityTypeUuid;
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public static EntitySyncStatus create(String entityName, LocalDateTime loadedSince, String uuid, String entityTypeUuid) {
        return new EntitySyncStatus(entityName, loadedSince, uuid, entityTypeUuid);
    }
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }
    
    public void setEntityTypeUuid(String entityTypeUuid) {
        this.entityTypeUuid = entityTypeUuid;
    }
    
    public LocalDateTime getLoadedSince() {
        return loadedSince;
    }
    
    public void setLoadedSince(LocalDateTime loadedSince) {
        this.loadedSince = loadedSince;
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
        EntitySyncStatus that = (EntitySyncStatus) o;
        return Objects.equals(uuid, that.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
    
    @Override
    public String toString() {
        return "EntitySyncStatus{" +
                "uuid='" + uuid + '\'' +
                ", entityName='" + entityName + '\'' +
                ", entityTypeUuid='" + entityTypeUuid + '\'' +
                ", loadedSince=" + loadedSince +
                ", lastModifiedDateTime=" + lastModifiedDateTime +
                ", voided=" + voided +
                '}';
    }
}