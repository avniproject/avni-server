package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectionInfoResponse {

    private String name;
    private String id;
    private boolean isPersonal;

    public CollectionInfoResponse() {
    }

    public CollectionInfoResponse(@JsonProperty("name") String name,
                                  @JsonProperty("id") Object id,
                                  @JsonProperty("is_personal") boolean isPersonal) {
        this.name = name;
        this.isPersonal = isPersonal;

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

    public boolean isPersonal() {
        return isPersonal;
    }

    public void setPersonal(boolean personal) {
        isPersonal = personal;
    }
}
