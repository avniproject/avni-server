package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Database;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.MetabaseRequestFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.*;

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

    public void grantOrgDatabaseAccessForOrgGroup(Database database, Group group) {
        String url = metabaseApiUrl + "/permissions/graph/db/" + database.getId();
        Map<String, Object> dbPermissions = getMapResponse(url);

        Map<String, Object> request = new HashMap<>();
        request.put("revision", dbPermissions.get("revision"));
        HashMap<Object, Object> groupsRequest = new HashMap<>();
        request.put("groups", groupsRequest);

        HashMap<Object, Object> groupRequest = new HashMap<>();
        HashMap<Object, Object> databaseRequest = new HashMap<>();
        databaseRequest.put("create-queries", "query-builder-and-native");
        databaseRequest.put("view-data", "unrestricted");
        databaseRequest.put("download", Map.of("schemas", "full"));
        groupRequest.put(String.valueOf(database.getId()), databaseRequest);
        groupsRequest.put(group.getId(), groupRequest);

        sendPutRequest(metabaseApiUrl + "/permissions/graph", request);
    }
}
