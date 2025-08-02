package org.avni.camp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Metadata information about entities that can be synchronized.
 * Defines sync behavior, API endpoints, and entity relationships.
 */
public class EntityMetaData {
    
    public enum EntityType {
        REFERENCE("reference"),  // Master data
        TRANSACTION("tx");       // User data
        
        private final String value;
        
        EntityType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    @JsonProperty("entityName")
    private String entityName;
    
    @JsonProperty("entityClass")
    private String entityClass;
    
    @JsonProperty("resourceName")
    private String resourceName;
    
    @JsonProperty("resourceUrl")
    private String resourceUrl;
    
    @JsonProperty("resourceSearchFilterURL")
    private String resourceSearchFilterURL;
    
    @JsonProperty("type")
    private EntityType type;
    
    @JsonProperty("syncWeight")
    private double syncWeight = 1.0;
    
    @JsonProperty("schemaName")
    private String schemaName;
    
    @JsonProperty("nameTranslated")
    private boolean nameTranslated = false;
    
    @JsonProperty("apiVersion")
    private String apiVersion;
    
    @JsonProperty("privilegeParam")
    private String privilegeParam;
    
    @JsonProperty("privilegeEntity")
    private String privilegeEntity;
    
    @JsonProperty("privilegeName")
    private String privilegeName;
    
    @JsonProperty("apiQueryParams")
    private Map<String, Object> apiQueryParams;
    
    @JsonProperty("apiQueryParamKey")
    private String apiQueryParamKey;
    
    @JsonProperty("hasMoreThanOneAssociation")
    private boolean hasMoreThanOneAssociation = false;
    
    // Parent relationship for nested entities
    @JsonProperty("parent")
    private EntityMetaData parent;
    
    // Associated sync status
    private EntitySyncStatus syncStatus;
    
    public EntityMetaData() {}
    
    public EntityMetaData(String entityName, EntityType type, String resourceName, double syncWeight) {
        this.entityName = entityName;
        this.type = type;
        this.resourceName = resourceName;
        this.syncWeight = syncWeight;
        this.schemaName = entityName;
    }
    
    // Static factory methods for common entity types
    public static EntityMetaData createReferenceEntity(String entityName, String resourceName, double syncWeight) {
        return new EntityMetaData(entityName, EntityType.REFERENCE, resourceName, syncWeight);
    }
    
    public static EntityMetaData createTransactionEntity(String entityName, String resourceName, double syncWeight) {
        return new EntityMetaData(entityName, EntityType.TRANSACTION, resourceName, syncWeight);
    }
    
    public boolean isReferenceData() {
        return EntityType.REFERENCE.equals(type);
    }
    
    public boolean isTransactionData() {
        return EntityType.TRANSACTION.equals(type);
    }
    
    // Getters and Setters
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getEntityClass() {
        return entityClass;
    }
    
    public void setEntityClass(String entityClass) {
        this.entityClass = entityClass;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getResourceUrl() {
        return resourceUrl;
    }
    
    public void setResourceUrl(String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }
    
    public String getResourceSearchFilterURL() {
        return resourceSearchFilterURL;
    }
    
    public void setResourceSearchFilterURL(String resourceSearchFilterURL) {
        this.resourceSearchFilterURL = resourceSearchFilterURL;
    }
    
    public EntityType getType() {
        return type;
    }
    
    public void setType(EntityType type) {
        this.type = type;
    }
    
    public double getSyncWeight() {
        return syncWeight;
    }
    
    public void setSyncWeight(double syncWeight) {
        this.syncWeight = syncWeight;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
    
    public boolean isNameTranslated() {
        return nameTranslated;
    }
    
    public void setNameTranslated(boolean nameTranslated) {
        this.nameTranslated = nameTranslated;
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public String getPrivilegeParam() {
        return privilegeParam;
    }
    
    public void setPrivilegeParam(String privilegeParam) {
        this.privilegeParam = privilegeParam;
    }
    
    public String getPrivilegeEntity() {
        return privilegeEntity;
    }
    
    public void setPrivilegeEntity(String privilegeEntity) {
        this.privilegeEntity = privilegeEntity;
    }
    
    public String getPrivilegeName() {
        return privilegeName;
    }
    
    public void setPrivilegeName(String privilegeName) {
        this.privilegeName = privilegeName;
    }
    
    public Map<String, Object> getApiQueryParams() {
        return apiQueryParams;
    }
    
    public void setApiQueryParams(Map<String, Object> apiQueryParams) {
        this.apiQueryParams = apiQueryParams;
    }
    
    public String getApiQueryParamKey() {
        return apiQueryParamKey;
    }
    
    public void setApiQueryParamKey(String apiQueryParamKey) {
        this.apiQueryParamKey = apiQueryParamKey;
    }
    
    public boolean isHasMoreThanOneAssociation() {
        return hasMoreThanOneAssociation;
    }
    
    public void setHasMoreThanOneAssociation(boolean hasMoreThanOneAssociation) {
        this.hasMoreThanOneAssociation = hasMoreThanOneAssociation;
    }
    
    public EntityMetaData getParent() {
        return parent;
    }
    
    public void setParent(EntityMetaData parent) {
        this.parent = parent;
    }
    
    public EntitySyncStatus getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(EntitySyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public boolean isReferenceEntity() {
        return type == EntityType.REFERENCE;
    }   
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityMetaData that = (EntityMetaData) o;
        return Objects.equals(entityName, that.entityName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(entityName);
    }
    
    @Override
    public String toString() {
        return "EntityMetaData{" +
                "entityName='" + entityName + '\'' +
                ", type=" + type +
                ", resourceName='" + resourceName + '\'' +
                ", syncWeight=" + syncWeight +
                '}';
    }
}