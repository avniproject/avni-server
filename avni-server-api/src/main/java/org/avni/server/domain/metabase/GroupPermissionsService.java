package org.avni.server.domain.metabase;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class GroupPermissionsService {
    private GroupPermissionsGraphResponse permissionsGraph;

    public GroupPermissionsService(GroupPermissionsGraphResponse permissionsGraph) {
        this.permissionsGraph = permissionsGraph;
    }

    public void updatePermissions(int groupId, int databaseId) {
        Map<String, Object> groups = permissionsGraph.getGroups();

        // Create permissions for the specified database
        Map<String, Object> databasePermissions = createDatabasePermissions();

        // Add new group with the database permissions to the graph
        addNewGroupWithPermissions(groups, groupId, databaseId, databasePermissions);

        // Remove access for group "1" to the newly created database
        removeAccessForAllOtherGroups(groups, databaseId);
    }

    private void addNewGroupWithPermissions(Map<String, Object> groups, int groupId, int databaseId, Map<String, Object> databasePermissions) {
        // Add a new group with the specified database permissions
        Map<String, Object> groupPermissions = new HashMap<>();
        groupPermissions.put(String.valueOf(databaseId), databasePermissions);
        groups.put(String.valueOf(groupId), groupPermissions);
    }

    private Map<String, Object> createDatabasePermissions() {
        // Create the permissions map for a database
        Map<String, Object> databasePermissions = new HashMap<>();
        databasePermissions.put("data", new HashMap<String, String>() {{
            put("schemas", "all");
        }});
        return databasePermissions;
    }

    private void removeAccessForAllOtherGroups(Map<String, Object> groups, int databaseId) {
        // Ensure group "1" does not have access to the newly created database
        if (groups.containsKey("1")) {
            Map<String, Object> group1Permissions = (Map<String, Object>) groups.get("1");
            group1Permissions.put(String.valueOf(databaseId), createNoAccessPermissions());
        }
    }

    private Map<String, Object> createNoAccessPermissions() {
        // Create the permissions map indicating no access
        Map<String, Object> noAccessPermissions = new HashMap<>();
        noAccessPermissions.put("data", new HashMap<String, String>() {{
            put("native", "none");
            put("schemas", "none");
        }});
        return noAccessPermissions;
    }

    public Map<String, Object> getUpdatedPermissionsGraph() {
        Map<String, Object> updatedPermissionsGraph = new HashMap<>();
        updatedPermissionsGraph.put("groups", permissionsGraph.getGroups());
        updatedPermissionsGraph.put("revision", permissionsGraph.getRevision());
        return updatedPermissionsGraph;
    }

    public GroupPermissionsGraphResponse getPermissionsGraph() {
        return permissionsGraph;
    }

    @Override
    public String toString() {
        return "Permissions{" +
                "permissionsGraph=" + permissionsGraph +
                '}';
    }
}
