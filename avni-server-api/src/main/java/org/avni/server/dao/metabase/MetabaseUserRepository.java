package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.dao.GroupRepository;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.metabase.DatabaseService;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MetabaseUserRepository extends MetabaseConnector {
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final DatabaseService databaseService;
    private final MetabaseGroupRepository metabaseGroupRepository;

    public MetabaseUserRepository(RestTemplateBuilder restTemplateBuilder, GroupPermissionsRepository groupPermissionsRepository, DatabaseService databaseService, MetabaseGroupRepository metabaseGroupRepository) {
        super(restTemplateBuilder);
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.databaseService = databaseService;
        this.metabaseGroupRepository = metabaseGroupRepository;
    }

    public MetabaseUserResponse save(CreateUserRequest createUserRequest) {
        String url = metabaseApiUrl + "/user";
        return postForObject(url, createUserRequest, MetabaseUserResponse.class);
    }

    public MetabaseAllUsersResponse getAllUsers() {
        String url = metabaseApiUrl + "/user" + "?status=all";
        return getForObject(url, MetabaseAllUsersResponse.class);
    }

    public MetabaseUserData getUserFromEmail(String email) {
        MetabaseAllUsersResponse response = getAllUsers();
        return response.getData().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findAny()
                .orElse(null);
    }

    public boolean activeUserExists(String email, boolean excludeSuperAdmins) {
        MetabaseAllUsersResponse response = getAllUsers();
        return response.getData().stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email) && user.isActive()
                        && !(excludeSuperAdmins && user.getIsSuperuser()));
    }

    public boolean activeUserExists(String email) {
        return activeUserExists(email, false);
    }


    public boolean emailExists(String email) {
        MetabaseAllUsersResponse response = getAllUsers();
        return response.getData().stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email));
    }

    public boolean userExistsInCurrentOrgGroup(String email) {
        MetabaseAllUsersResponse response = getAllUsers();
        return response.getData().stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email) && user.getGroupIds().contains(databaseService.getGlobalMetabaseGroup().getId()));
    }

    public DeactivateMetabaseUserResponse deactivateMetabaseUser(String email) {
        String url = metabaseApiUrl + "/user/" + getUserFromEmail(email).getId();
        return deleteForObject(url, DeactivateMetabaseUserResponse.class);
    }

    public void reactivateMetabaseUser(String email) {
        String url = metabaseApiUrl + "/user/" + getUserFromEmail(email).getId() + "/reactivate";
        sendPutRequest(url, null);
    }

    public List<UserGroupMemberships> getUserGroupMemberships() {
        List<UserGroupMemberships> userGroupMemberships = new ArrayList<>();
        UserGroupMemberships defaultAllUsers = new UserGroupMemberships(1, false);
        userGroupMemberships.add(defaultAllUsers);
        Group group = metabaseGroupRepository.getGroup();
        if (group != null) {
            UserGroupMemberships currentOrganisationGroup = new UserGroupMemberships(group.getId(), false);
            userGroupMemberships.add(currentOrganisationGroup);
        }
        return userGroupMemberships;
    }

    public List<UpdateUserGroupResponse> updateGroupPermissions(UpdateUserGroupRequest updateUserGroupRequest) {
        String url = metabaseApiUrl + "/permissions" + "/membership";
        String jsonResponse = postForObject(url, updateUserGroupRequest, String.class);
        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response: " + e.getMessage(), e);
        }
    }
}
