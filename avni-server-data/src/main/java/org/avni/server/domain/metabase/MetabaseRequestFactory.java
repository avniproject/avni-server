package org.avni.server.domain.metabase;

import org.avni.server.util.MapUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MetabaseRequestFactory {
    public static Map<String, Object> deriveRequestToRestrictPermissionToOrgDatabase(Map<String, Object> groupPermissionResponse, int groupId, int databaseId) {
        Map<String, Object> request = new HashMap<>();
        request.put("revision", groupPermissionResponse.get("revision"));
        Map<String, Object> queryRequest = new HashMap<>();
        queryRequest.put("create-queries", "query-builder-and-native");
        HashMap<Object, Object> orgUserGroupRequest = new HashMap<>();
        orgUserGroupRequest.put(String.valueOf(databaseId), queryRequest);
        MapUtil.setValueAtPath(request, new String[]{"groups", String.valueOf(groupId)}, orgUserGroupRequest);

        return request;
    }

    public static Map<String, Object> deriveRequestToUpdateCollectionPermissions(Map<String, Object> permissions, int collectionId, Group group) {
        Map<String, Object> request = new HashMap<>();
        request.put("revision", permissions.get("revision"));
        HashMap<Object, Object> allUsersGroupRequest = new HashMap<>();
        allUsersGroupRequest.put(String.valueOf(collectionId), "none");
        HashMap<Object, Object> orgUserGroupRequest = new HashMap<>();
        orgUserGroupRequest.put(String.valueOf(collectionId), "write");
        Map<String, Object> groups = new HashMap<>();
        groups.put("1", allUsersGroupRequest);
        groups.put(String.valueOf(group.getId()), orgUserGroupRequest);
        request.put("groups", groups);
        return request;
    }
}
