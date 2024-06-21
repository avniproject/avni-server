package org.avni.server.domain.metabase;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Component
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupPermissionsGraphResponse {
    @JsonProperty("groups")
    private Map<String, Object> groups;

    @JsonProperty("revision")
    private int revision;

    public Map<String, Object> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, Object> groups) {
        this.groups = groups;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    @Override
    public String toString() {
        return "PermissionsGraphResponse{" +
                "groups=" + groups +
                ", revision=" + revision +
                '}';
    }
}
