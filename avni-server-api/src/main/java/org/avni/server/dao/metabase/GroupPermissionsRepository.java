package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.GroupPermissionsService;
import org.avni.server.domain.metabase.GroupPermissionsGraphResponse;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.GroupPermissionsBody;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class GroupPermissionsRepository extends MetabaseConnector {

    public GroupPermissionsRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

        public Group save(Group permissionsGroup) {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionsBody body = new GroupPermissionsBody(permissionsGroup.getName());
        HttpEntity<Map<String, Object>> entity = createJsonEntity(body);

        Group response = restTemplate.postForObject(url, entity, Group.class);
        return response;
    }

    public GroupPermissionsGraphResponse getPermissionsGraph() {
        String url = metabaseApiUrl + "/permissions/graph";
        return getForObject(url, GroupPermissionsGraphResponse.class);
    }

    public void updatePermissionsGraph(GroupPermissionsService permissions, int groupId, int databaseId) {
        String url = metabaseApiUrl + "/permissions/graph";
        Map<String, Object> requestBody = permissions.getUpdatedPermissionsGraph();
        sendPutRequest(url, requestBody);
    }
}
