package org.avni.server.domain.metabase;

import java.util.HashMap;
import java.util.Map;

public class Permissions {
    private Map<String, Object> permissionsGraph;

    public Permissions(Map<String, Object> permissionsGraph) {
        this.permissionsGraph = permissionsGraph;
    }

    public Map<String, Object> getPermissionsGraph() {
        return permissionsGraph;
    }

    public void updatePermissionsGraph(int groupId, int databaseId) {
        Map<String, Object> groups = (Map<String, Object>) permissionsGraph.get("groups");

        Map<String, Object> databasePermissions = new HashMap<>();
        databasePermissions.put("data", new HashMap<String, String>() {{
            put("schemas", "all");
        }});

        groups.computeIfAbsent(String.valueOf(groupId), k -> new HashMap<>());
        Map<String, Object> groupPermissions = (Map<String, Object>) groups.get(String.valueOf(groupId));
        groupPermissions.put(String.valueOf(databaseId), databasePermissions);

        if (groups.containsKey("1")) {
            Map<String, Object> allGroupsPermissionsMap = (Map<String, Object>) groups.get("1");
            if (allGroupsPermissionsMap.containsKey(String.valueOf(databaseId))) {
                Map<String, Object> allGroupsDatabasePermissions = (Map<String, Object>) allGroupsPermissionsMap.get(String.valueOf(databaseId));
                if (allGroupsDatabasePermissions.containsKey("data")) {
                    Map<String, String> dataPermissions = (Map<String, String>) allGroupsDatabasePermissions.get("data");
                    dataPermissions.put("native", "none");
                    dataPermissions.put("schemas", "none");
                }
            }
        }
    }
}