package org.avni.server.domain.metabase;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class CollectionPermissionsService {
    private final CollectionPermissionsGraphResponse permissionsGraph;

    public CollectionPermissionsService(CollectionPermissionsGraphResponse permissionsGraph) {
        this.permissionsGraph = permissionsGraph;
    }

    public void updatePermissions(int groupId, int collectionId) {
        Map<String, Map<String, String>> groups = permissionsGraph.getGroups();

        ensureGroupsMapExists(groups);

        Map<String, String> groupPermissions = getOrCreateGroupPermissions(groups, groupId);
        updateGroupPermissions(groupPermissions, collectionId);

        handleSpecialGroupPermissions(groups, collectionId);
    }

    private void ensureGroupsMapExists(Map<String, Map<String, String>> groupsMap) {
        if (groupsMap == null) {
            throw new RuntimeException("Groups not found in the collection permissions graph.");
        }
    }

    private Map<String, String> getOrCreateGroupPermissions(Map<String, Map<String, String>> groupsMap, int groupId) {
        return groupsMap.computeIfAbsent(String.valueOf(groupId), k -> new HashMap<>());
    }

    private void updateGroupPermissions(Map<String, String> groupPermissions, int collectionId) {
        groupPermissions.put(String.valueOf(collectionId), "write");
    }

    private void handleSpecialGroupPermissions(Map<String, Map<String, String>> groupsMap, int collectionId) {
        if (groupsMap.containsKey("1")) {
            Map<String, String> group1Permissions = groupsMap.get("1");
            group1Permissions.put(String.valueOf(collectionId), "none");
        }
    }

    public Map<String, Object> getUpdatedPermissionsGraph() {
        Map<String, Object> updatedPermissionsGraph = new HashMap<>();
        updatedPermissionsGraph.put("groups", permissionsGraph.getGroups());
        updatedPermissionsGraph.put("revision", permissionsGraph.getRevision());
        return updatedPermissionsGraph;
    }

    public CollectionPermissionsGraphResponse getPermissionsGraph() {
        return permissionsGraph;
    }
}
