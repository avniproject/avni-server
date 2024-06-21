package org.avni.server.domain.metabase;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class CollectionPermissionsGraphResponse {
    private int revision;
    private Map<String, Map<String, String>> groups;

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Map<String, Map<String, String>> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, Map<String, String>> groups) {
        this.groups = groups;
    }
}
