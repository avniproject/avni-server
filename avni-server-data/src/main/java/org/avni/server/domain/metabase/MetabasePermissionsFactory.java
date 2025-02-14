package org.avni.server.domain.metabase;

import org.avni.server.util.MapUtil;

import java.util.HashMap;
import java.util.Map;

public class MetabasePermissionsFactory {
    public static Map<String, Object> createRequestForDatabasePermissionToGroup(int groupId, int databaseId) {
        Map<String, Object> databasePermissions = MapUtil.setValueAtPath(new HashMap<>(), new String[]{"data", "schemas"}, "all");
        return MapUtil.setValueAtPath(new HashMap<>(), new String[]{String.valueOf(groupId), String.valueOf(databaseId)}, databasePermissions);
    }

    public static Map<String, Object> createRequestToRemoveDatabaseAccessToAllUsers(Map<String, Object> currentPermissionsForAllUsers, int databaseId) {
        Map<String, Object> groupDatabaseAccess = MapUtil.getValueFromPath(currentPermissionsForAllUsers, new String[]{"groups", "1", String.valueOf(databaseId)});
        groupDatabaseAccess.clear();
        groupDatabaseAccess.put("create-queries", "no");
        groupDatabaseAccess.put("view-data", "unrestricted");
        MapUtil.setValueAtPath(groupDatabaseAccess, new String[]{"download", "schemas"}, "full");
        return currentPermissionsForAllUsers;
    }
}
