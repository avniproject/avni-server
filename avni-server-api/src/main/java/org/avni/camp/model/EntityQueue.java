package org.avni.camp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an entity queued for upload to the server.
 * Contains information about the entity awaiting synchronization.
 */
public class EntityQueue {
    
    /**
     * Action to be performed on the entity
     */
    public enum Action {
        CREATE, UPDATE, DELETE
    }
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("entityUuid")
    private String entityUuid;
    
    @JsonProperty("entity")
    private String entity;
    
    @JsonProperty("entityType")
    private String entityType;
    
    @JsonProperty("action")
    private Action action;
    
    @JsonProperty("savedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime savedAt;
    
    @JsonProperty("createdDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdDateTime;
    
    @JsonProperty("processed")
    private boolean processed = false;
    
    @JsonProperty("lastModifiedDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastModifiedDateTime;
    
    @JsonProperty("voided")
    private boolean voided = false;
    
    public EntityQueue() {
        this.uuid = UUID.randomUUID().toString();
        this.savedAt = LocalDateTime.now();
        this.createdDateTime = LocalDateTime.now();
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public EntityQueue(String entityUuid, String entity) {
        this();
        this.entityUuid = entityUuid;
        this.entity = entity;
        this.entityType = entity; // Default entityType to entity name
    }
    
    public static EntityQueue create(String entityUuid, String entity) {
        return new EntityQueue(entityUuid, entity);
    }
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getEntityUuid() {
        return entityUuid;
    }
    
    public void setEntityUuid(String entityUuid) {
        this.entityUuid = entityUuid;
    }
    
    public String getEntity() {
        return entity;
    }
    
    public void setEntity(String entity) {
        this.entity = entity;
    }
    
    public LocalDateTime getSavedAt() {
        return savedAt;
    }
    
    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
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
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public Action getAction() {
        return action;
    }
    
    public void setAction(Action action) {
        this.action = action;
    }
    
    public LocalDateTime getCreatedDateTime() {
        return createdDateTime;
    }
    
    public void setCreatedDateTime(LocalDateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }
    
    public boolean isProcessed() {
        return processed;
    }
    
    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityQueue that = (EntityQueue) o;
        return Objects.equals(uuid, that.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
    
    @Override
    public String toString() {
        return "EntityQueue{" +
                "uuid='" + uuid + '\'' +
                ", entityUuid='" + entityUuid + '\'' +
                ", entity='" + entity + '\'' +
                ", savedAt=" + savedAt +
                '}';
    }
}