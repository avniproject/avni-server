package org.avni.server.domain.metabase;

import java.util.HashMap;
import java.util.Map;

public class CollectionPermissions {
    private Map<String, Object> permissionsGraph;

    public CollectionPermissions(Map<String, Object> permissionsGraph) {
        this.permissionsGraph = permissionsGraph;
    }

    public Map<String, Object> getPermissionsGraph() {
        return permissionsGraph;
    }

    public void updatePermissionsGraph(int groupId, int collectionId) {
        Map<String, Map<String, String>> groups = (Map<String, Map<String, String>>) permissionsGraph.get("groups");

        groups.computeIfAbsent(String.valueOf(groupId), k -> new HashMap<>());
        Map<String, String> groupPermissions = groups.get(String.valueOf(groupId));
        groupPermissions.put(String.valueOf(collectionId), "write");

        if (groups.containsKey("1")) {
            Map<String, String> allGroupPermissions = groups.get("1");
            allGroupPermissions.put(String.valueOf(collectionId), "none");
        }
    }
}
