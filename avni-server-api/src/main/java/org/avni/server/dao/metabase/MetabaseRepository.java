package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.CollectionPermissions;
import org.avni.server.domain.metabase.Permissions;
import org.avni.server.domain.metabase.PermissionsGroup;
import org.avni.server.domain.metabase.PermissionsGroupResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class MetabaseRepository extends BaseMetabaseRepository{

    public MetabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public PermissionsGroupResponse createPermissionsGroup(PermissionsGroup permissionsGroup) {
        String url = metabaseApiUrl + "/permissions/group";
        HttpEntity<PermissionsGroup> entity = createHttpEntity(permissionsGroup);
        PermissionsGroupResponse response = restTemplate.postForObject(url, entity, PermissionsGroupResponse.class);

        return response;
    }

    public Map<String, Object> getPermissionsGraph() {
        String url = metabaseApiUrl + "/permissions/graph";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createHttpEntity(null), Map.class);
        return response.getBody();
    }

    public Map<String, Object> getCollectionPermissionsGraph() {
        String url = metabaseApiUrl + "/collection/graph";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createHttpEntity(null), Map.class);
        return response.getBody();
    }

    public void assignDatabasePermissions(Permissions permissions, int groupId, int databaseId) {
        permissions.updatePermissionsGraph(groupId, databaseId);
        String url = metabaseApiUrl + "/permissions/graph";
        sendPutRequest(url, permissions.getPermissionsGraph());
    }

    public void updateCollectionPermissions(CollectionPermissions collectionPermissions, int groupId, int collectionId) {
        collectionPermissions.updatePermissionsGraph(groupId, collectionId);
        String url = metabaseApiUrl + "/collection/graph";
        sendPutRequest(url, collectionPermissions.getPermissionsGraph());
    }
}
