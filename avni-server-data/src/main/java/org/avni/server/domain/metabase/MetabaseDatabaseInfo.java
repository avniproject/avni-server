package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetabaseDatabaseInfo {

    @JsonProperty("description")
    private String description;

    @JsonProperty("features")
    private List<String> features;

    @JsonProperty("cache_field_values_schedule")
    private String cacheFieldValuesSchedule;

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("auto_run_queries")
    private boolean autoRunQueries;

    @JsonProperty("metadata_sync_schedule")
    private String metadataSyncSchedule;

    @JsonProperty("name")
    private String name;

    @JsonProperty("tables")
    private List<TableDetails> tables;

    public String getDescription() {
        return description;
    }

    public List<String> getFeatures() {
        return features;
    }

    public String getCacheFieldValuesSchedule() {
        return cacheFieldValuesSchedule;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isAutoRunQueries() {
        return autoRunQueries;
    }

    public String getMetadataSyncSchedule() {
        return metadataSyncSchedule;
    }

    public String getName() {
        return name;
    }

    public List<TableDetails> getTables() {
        return tables;
    }
}
