package org.avni.server.domain.metabase;

import org.avni.server.util.MapUtil;

import java.util.Map;
import java.util.Set;

public class MetabaseRequestFactory {
    public static Map<String, Object> deriveRequestToRestrictPermissionToOrgDatabase(Map<String, Object> groupPermissionResponse, int groupId, int databaseId) {
        Map<String, Object> groupPermission = MapUtil.getValueFromPath(groupPermissionResponse, new String[]{"groups", String.valueOf(groupId)});
        Set<String> databaseIds = groupPermission.keySet();
        for (String id : databaseIds) {
            if (!id.equals(String.valueOf(databaseId))) {
                Map<String, Object> groupDatabasePermission = (Map<String, Object>) groupPermission.get(id);
                groupDatabasePermission.put("create-queries", "no");
            }
        }
        return groupPermissionResponse;
    }

    public static Map<String, Object> createRequestToRemoveDatabaseAccessForAllUsers(Map<String, Object> permissionsForAllUsers, int databaseId) {
        Map<String, Object> groupDatabaseAccess = MapUtil.getValueFromPath(permissionsForAllUsers, new String[]{"groups", "1", String.valueOf(databaseId)});
        groupDatabaseAccess.clear();
        groupDatabaseAccess.put("create-queries", "no");
        groupDatabaseAccess.put("view-data", "unrestricted");
        MapUtil.setValueAtPath(groupDatabaseAccess, new String[]{"download", "schemas"}, "full");
        return permissionsForAllUsers;
    }

    public static Map<String, Object> derviceRequestToUpdateCollectionPermissions(Map<String, Object> permissions, int groupId, int collectionId) {
        Map<String, Object> group = MapUtil.getValueFromPath(permissions, new String[]{"groups", String.valueOf(groupId)});
        group.put(String.valueOf(collectionId), "write");
        group.put("2", "none");
        return permissions;
    }
}
