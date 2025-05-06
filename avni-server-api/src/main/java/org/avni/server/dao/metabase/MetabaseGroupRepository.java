package org.avni.server.dao.metabase;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.GroupPermissionResponse;
import org.avni.server.domain.metabase.GroupPermissionsBody;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Repository
public class MetabaseGroupRepository extends MetabaseConnector {
    private static final Logger logger = LoggerFactory.getLogger(MetabaseGroupRepository.class);

    public MetabaseGroupRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public Group createGroup(String name) {
        String url = metabaseApiUrl + "/permissions/group";
        GroupPermissionsBody body = new GroupPermissionsBody(name);
        try {
            HttpEntity<Map<String, Object>> entity = createJsonEntity(body);
            return restTemplate.postForObject(url, entity, Group.class);
        } catch (Exception e) {
            logger.error("Create group failed for: {} and {}", url, ObjectMapperSingleton.writeValueAsStringSafe(body));
            throw new RuntimeException(e);
        }
    }

    public Group createGroup(Organisation organisation) {
        return createGroup(organisation.getName());
    }

    public Group findGroup(String name) {
        try {
            List<GroupPermissionResponse> existingGroups = getAllGroups();
            for (GroupPermissionResponse group : existingGroups) {
                if (group.getName().equalsIgnoreCase(name)) {
                    return new Group(group.getName(), group.getId());
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Find group failed for name: {}", name);
            throw new RuntimeException(e);
        }
    }

    public Group findGroup(Organisation organisation) {
        return findGroup(organisation.getName());
    }

    public List<GroupPermissionResponse> getAllGroups() {
        String url = metabaseApiUrl + "/permissions/group";
        try {
            GroupPermissionResponse[] response = getForObject(url, GroupPermissionResponse[].class);
            return Arrays.asList(response);
        } catch (Exception e) {
            logger.error("Get all groups failed for: {}", url);
            throw new RuntimeException(e);
        }
    }

    public Group getGroup() {
        String name = UserContextHolder.getOrganisation().getName();
        return findGroup(name);
    }
}
