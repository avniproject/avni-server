package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MetabaseUserRepository extends MetabaseConnector {
    private final MetabaseGroupRepository metabaseGroupRepository;
    private static final Logger logger = LoggerFactory.getLogger(MetabaseUserRepository.class);

    public MetabaseUserRepository(RestTemplateBuilder restTemplateBuilder, MetabaseGroupRepository metabaseGroupRepository) {
        super(restTemplateBuilder);
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

    public Map<String, List<MetabaseGroupMembership>> getAllMemberships() {
        String url = metabaseApiUrl + "/permissions/membership";
        return getForObject(url, new ParameterizedTypeReference<Map<String, List<MetabaseGroupMembership>>>() {});
    }

    public void deleteMembership(MetabaseGroupMembership membership) {
        String url = metabaseApiUrl + "/permissions/membership/" + membership.membershipId();
        deleteForObject(url, String.class);
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
        Group group = metabaseGroupRepository.findGroup(UserContextHolder.getOrganisation().getName());
        return response.getData().stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email) && user.getGroupIds().contains(group.getId()));
    }

    public DeactivateMetabaseUserResponse deactivateMetabaseUser(String email) {
        String url = metabaseApiUrl + "/user/" + getUserFromEmail(email).getId();
        return deleteForObject(url, DeactivateMetabaseUserResponse.class);
    }

    public void reactivateMetabaseUser(String email) {
        String url = metabaseApiUrl + "/user/" + getUserFromEmail(email).getId() + "/reactivate";
        sendPutRequest(url, null);
    }

    public List<UserGroupMemberships> buildDefaultUserGroupMemberships(Group orgMetabaseGroup) {
        List<UserGroupMemberships> userGroupMemberships = buildAllUserGroupMembership();
        if (orgMetabaseGroup == null) orgMetabaseGroup = metabaseGroupRepository.getGroup();
        if (orgMetabaseGroup != null) {
            UserGroupMemberships currentOrganisationGroup = new UserGroupMemberships(orgMetabaseGroup.getId(), false);
            userGroupMemberships.add(currentOrganisationGroup);
        }
        return userGroupMemberships;
    }

    public List<UserGroupMemberships> buildAllUserGroupMembership() {
        List<UserGroupMemberships> userGroupMemberships = new ArrayList<>();
        UserGroupMemberships defaultAllUsers = new UserGroupMemberships(1, false);
        userGroupMemberships.add(defaultAllUsers);
        return userGroupMemberships;
    }

    public List<UpdateUserGroupResponse> updateGroupPermissions(UpdateUserGroupRequest updateUserGroupRequest) {
        String url = metabaseApiUrl + "/permissions" + "/membership";
        try {
            String jsonResponse = postForObject(url, updateUserGroupRequest, String.class);
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, new TypeReference<>() {});
        } catch (RuntimeException e) {
            logger.error("Update group permissions failed for: {}", url);
            throw e;
        } catch (Exception e) {
            logger.error("Update group permissions failed for: {}", url);
            throw new RuntimeException(e);
        }
    }
}
