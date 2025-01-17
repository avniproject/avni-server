package org.avni.server.dao.metabase;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Repository
public class GroupPermissionsRepository extends MetabaseConnector {
    private final OrganisationService organisationService;

    public GroupPermissionsRepository(RestTemplateBuilder restTemplateBuilder, OrganisationService organisationService) {
        super(restTemplateBuilder);
        this.organisationService = organisationService;
    }

    public Group save(Group permissionsGroup) {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionsBody body = new GroupPermissionsBody(permissionsGroup.getName());
        HttpEntity<Map<String, Object>> entity = createJsonEntity(body);
        return restTemplate.postForObject(url, entity, Group.class);
    }

    public GroupPermissionsGraphResponse getPermissionsGraph() {
        String url = metabaseApiUrl + "/permissions/graph";
        return getForObject(url, GroupPermissionsGraphResponse.class);
    }

    public void updatePermissionsGraph(GroupPermissionsService permissions) {
        String url = metabaseApiUrl + "/permissions/graph";
        Map<String, Object> requestBody = permissions.getUpdatedPermissionsGraph();
        sendPutRequest(url, requestBody);
    }

    public List<GroupPermissionResponse> getAllGroups() {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionResponse[] response = getForObject(url, GroupPermissionResponse[].class);
        return Arrays.asList(response);
    }

    public void updateGroupPermissions(int groupId, int databaseId) {
        GroupPermissionsService groupPermissions = new GroupPermissionsService(getPermissionsGraph());
        groupPermissions.updatePermissions(groupId, databaseId);
        updatePermissionsGraph(groupPermissions);
    }

    public Group getCurrentOrganisationGroup(){
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        return findGroup(currentOrganisation.getName());
    }

    public Group findGroup(String name){
        List<GroupPermissionResponse> existingGroups = getAllGroups();

        for (GroupPermissionResponse group : existingGroups) {
            if (group.getName().equals(name)) {
                return new Group(group.getName(), group.getId());
            }
        }
        return null;
    }

    public Group CreateGroup(String name, int databaseId) {
        Group newGroup = save(new Group(name));
        updateGroupPermissions(newGroup.getId(), databaseId);
        return newGroup;
    }


}
