package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableDetails {

    @JsonProperty("description")
    private String description;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("name")
    private String name;

    @JsonProperty("active")
    private boolean active;

    @JsonProperty("id")
    private int id;

    @JsonProperty("db_id")
    private int dbId;

    @JsonProperty("initial_sync_status")
    private String initialSyncStatus;

    @JsonProperty("display_name")
    private String displayName;

    public String getDescription() {
        return description;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public int getId() {
        return id;
    }

    public int getDbId() {
        return dbId;
    }

    public String getInitialSyncStatus() {
        return initialSyncStatus;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean nameMatches(String tableName) {
        return tableName.equals(getDisplayName());
    }
}
