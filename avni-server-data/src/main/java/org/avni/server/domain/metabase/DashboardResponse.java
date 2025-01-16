package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardResponse {

    private String name;
    private String id;

    public DashboardResponse() {
    }

    public DashboardResponse(@JsonProperty("name") String name,
                                  @JsonProperty("id") Object id) {
        this.name = name;

        if (id instanceof Integer) {
            this.id = String.valueOf(id);
        } else {
            this.id = id.toString();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public int getIdAsInt() {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Failed to convert id to integer: " + id, e);
        }
    }
}
