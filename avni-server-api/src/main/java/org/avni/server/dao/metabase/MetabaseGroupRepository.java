package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.GroupPermissionResponse;
import org.avni.server.domain.metabase.GroupPermissionsBody;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Repository
public class MetabaseGroupRepository extends MetabaseConnector {
    public MetabaseGroupRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }
    public Group createGroup(String name) {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionsBody body = new GroupPermissionsBody(new Group(name).getName());
        HttpEntity<Map<String, Object>> entity = createJsonEntity(body);
        return restTemplate.postForObject(url, entity, Group.class);
    }

    public Group findGroup(String name) {
        List<GroupPermissionResponse> existingGroups = getAllGroups();

        for (GroupPermissionResponse group : existingGroups) {
            if (group.getName().equals(name)) {
                return new Group(group.getName(), group.getId());
            }
        }
        return null;
    }

    public List<GroupPermissionResponse> getAllGroups() {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionResponse[] response = getForObject(url, GroupPermissionResponse[].class);
        return Arrays.asList(response);
    }

    public Group getGroup() {
        String name = UserContextHolder.getOrganisation().getName();
        return findGroup(name);
    }
}
