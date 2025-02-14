package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.domain.metabase.Database;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.MetabaseRequestFactory;
import org.avni.server.util.MapUtil;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.*;

import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

@Repository
public class GroupPermissionsRepository extends MetabaseConnector {
    public GroupPermissionsRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    private Map<String, Object> getGroupPermission(int groupId) {
        String url = metabaseApiUrl + "/permissions/graph/group/" + groupId;
        return getMapResponse(url);
    }

    public void restrictGroupAccessToItsOwnDatabaseOnly(int groupId, int databaseId) {
        Map<String, Object> groupPermission = this.getGroupPermission(groupId);
        Map<String, Object> requestForDatabasePermissionToGroup = MetabaseRequestFactory.deriveRequestToRestrictPermissionToOrgDatabase(groupPermission, groupId, databaseId);
        String url = metabaseApiUrl + "/permissions/graph";
        sendPutRequest(url, requestForDatabasePermissionToGroup);
    }

    public void delete(Group group) {
        String url = metabaseApiUrl + "/permissions/group/" + group.getId();
        deleteForObject(url, Void.class);
    }

    public void removeAllUsersPermissionToOrgDatabase(Database database) {
        Map<String, Object> allUsersPermission = this.getAllUsersPermission();
        String url = metabaseApiUrl + "/permissions/graph";
        Map<String, Object> request = MetabaseRequestFactory.createRequestToRemoveDatabaseAccessForAllUsers(allUsersPermission, database.getId());
        sendPutRequest(url, request);
    }

    private Map<String, Object> getAllUsersPermission() {
        String url = metabaseApiUrl + "/permissions/graph/group/1";
        return getMapResponse(url);
    }

    public void removeOrgDatabaseAccessForAllOtherGroups(Database database, Group group) {
        String url = metabaseApiUrl + "/permissions/graph/db/" + database.getId();
        Map<String, Object> dbPermissions = getMapResponse(url);
        Map<String, Object> groups = (Map<String, Object>) dbPermissions.get("groups");
        List<String> skip = Arrays.asList("1", "2", String.valueOf(group.getId()));

        Map<String, Object> request = new HashMap<>();
        request.put("revision", dbPermissions.get("revision"));
        HashMap<Object, Object> groupsRequest = new HashMap<>();
        request.put("groups", groupsRequest);
        for (String groupId : groups.keySet()) {
            if (!skip.contains(groupId)) {
                HashMap<Object, Object> groupRequest = new HashMap<>();
                HashMap<Object, Object> databaseRequest = new HashMap<>();
                databaseRequest.put("create-queries", "no");
                databaseRequest.put("view-data", "unrestricted");
                databaseRequest.put("download", Map.of("schemas", "full"));
                groupRequest.put(String.valueOf(database.getId()), databaseRequest);
                groupsRequest.put(groupId, groupRequest);
            }
        }
        sendPutRequest(metabaseApiUrl + "/permissions/graph", request);
    }
}
