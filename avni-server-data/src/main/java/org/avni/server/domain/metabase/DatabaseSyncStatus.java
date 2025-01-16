package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseSyncStatus {

    @JsonProperty("initial_sync_status")
    private String initialSyncStatus;

    @JsonProperty("name")
    private String name;

    @JsonProperty("is_full_sync")
    private boolean isFullSync;

    @JsonProperty("id")
    private int id;

    @JsonProperty("engine")
    private String engine;


    public String getInitialSyncStatus() {
        return initialSyncStatus;
    }


    public String getName() {
        return name;
    }


    public boolean isFullSync() {
        return isFullSync;
    }


    public int getId() {
        return id;
    }


    public String getEngine() {
        return engine;
    }

}
