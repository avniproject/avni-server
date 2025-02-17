package org.avni.server.domain.metabase;

import org.avni.server.util.MapUtil;

import java.util.HashMap;
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

    public static Map<String, Object> deriveRequestToUpdateCollectionPermissions(Map<String, Object> permissions, int collectionId, Group group) {
        Map<String, Object> allUsersGroup = MapUtil.getValueFromPath(permissions, new String[]{"groups", "1"});
        Map<String, Object> orgUserGroup = MapUtil.getValueFromPath(permissions, new String[]{"groups", String.valueOf(group.getId())});

        Map<String, Object> request = new HashMap<>();
        request.put("revision", permissions.get("revision"));

        HashMap<Object, Object> allUserGroupRequest = new HashMap<>();
        MapUtil.setValueAtPath(request, new String[]{"groups", "1"}, allUserGroupRequest);
        allUserGroupRequest.putAll(allUsersGroup);
        allUserGroupRequest.put(String.valueOf(collectionId), "none");

        HashMap<Object, Object> orgUserGroupRequest = new HashMap<>();
        MapUtil.setValueAtPath(request, new String[]{"groups", String.valueOf(group.getId())}, orgUserGroupRequest);
        orgUserGroupRequest.putAll(orgUserGroup);
        orgUserGroupRequest.put(String.valueOf(collectionId), "write");
        return request;
    }
}
