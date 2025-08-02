package org.avni.camp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a media file queued for upload to the server.
 * Contains information about the file, its associated entity, and upload metadata.
 */
public class MediaQueue {
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("entityUuid")
    private String entityUuid;
    
    @JsonProperty("entityName")
    private String entityName;
    
    @JsonProperty("fileName")
    private String fileName;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("entityTargetField")
    private String entityTargetField = "observations";
    
    @JsonProperty("conceptUuid")
    private String conceptUuid;
    
    @JsonProperty("savedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime savedAt;
    
    @JsonProperty("createdDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdDateTime;
    
    @JsonProperty("lastModifiedDateTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastModifiedDateTime;
    
    @JsonProperty("uploaded")
    private boolean uploaded = false;
    
    @JsonProperty("voided")
    private boolean voided = false;
    
    public MediaQueue() {
        this.uuid = UUID.randomUUID().toString();
        this.savedAt = LocalDateTime.now();
        this.createdDateTime = LocalDateTime.now();
        this.lastModifiedDateTime = LocalDateTime.now();
    }
    
    public MediaQueue(String entityUuid, String entityName, String fileName, String type, String entityTargetField, String conceptUuid) {
        this();
        this.entityUuid = entityUuid;
        this.entityName = entityName;
        this.fileName = fileName;
        this.type = type;
        this.entityTargetField = entityTargetField != null ? entityTargetField : "observations";
        this.conceptUuid = conceptUuid;
    }
    
    public static MediaQueue create(String entityUuid, String entityName, String fileName, String type, String entityTargetField, String conceptUuid) {
        return new MediaQueue(entityUuid, entityName, fileName, type, entityTargetField, conceptUuid);
    }
    
    public String getDisplayText() {
        return String.format("%s (%s)", fileName, type);
    }
    
    public MediaQueue clone() {
        MediaQueue clone = new MediaQueue();
        clone.uuid = this.uuid;
        clone.entityUuid = this.entityUuid;
        clone.entityName = this.entityName;
        clone.fileName = this.fileName;
        clone.type = this.type;
        clone.entityTargetField = this.entityTargetField;
        clone.conceptUuid = this.conceptUuid;
        clone.savedAt = this.savedAt;
        clone.createdDateTime = this.createdDateTime;
        clone.lastModifiedDateTime = this.lastModifiedDateTime;
        clone.uploaded = this.uploaded;
        clone.voided = this.voided;
        return clone;
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
    
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getEntityTargetField() {
        return entityTargetField;
    }
    
    public void setEntityTargetField(String entityTargetField) {
        this.entityTargetField = entityTargetField;
    }
    
    public String getConceptUuid() {
        return conceptUuid;
    }
    
    public void setConceptUuid(String conceptUuid) {
        this.conceptUuid = conceptUuid;
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
    
    public LocalDateTime getCreatedDateTime() {
        return createdDateTime;
    }
    
    public void setCreatedDateTime(LocalDateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }
    
    public boolean isUploaded() {
        return uploaded;
    }
    
    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaQueue that = (MediaQueue) o;
        return Objects.equals(uuid, that.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
    
    @Override
    public String toString() {
        return "MediaQueue{" +
                "uuid='" + uuid + '\'' +
                ", entityUuid='" + entityUuid + '\'' +
                ", entityName='" + entityName + '\'' +
                ", fileName='" + fileName + '\'' +
                ", type='" + type + '\'' +
                ", entityTargetField='" + entityTargetField + '\'' +
                ", conceptUuid='" + conceptUuid + '\'' +
                ", savedAt=" + savedAt +
                '}';
    }
}