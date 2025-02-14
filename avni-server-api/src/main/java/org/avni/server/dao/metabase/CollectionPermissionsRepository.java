package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.domain.metabase.CollectionInfoResponse;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.MetabaseRequestFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.Map;

import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

@Repository
public class CollectionPermissionsRepository extends MetabaseConnector {
    public CollectionPermissionsRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    private Map<String, Object> getCollectionPermissionsGraph() {
        try {
            String url = metabaseApiUrl + "/collection/graph";
            String string = getForObject(url, String.class);
            return getObjectMapper().readValue(string, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateCollectionPermissions(Group group, CollectionInfoResponse collection) {
        Map<String, Object> collectionPermissionsGraph = this.getCollectionPermissionsGraph();
        Map<String, Object> request = MetabaseRequestFactory.derviceRequestToUpdateCollectionPermissions(collectionPermissionsGraph, group.getId(), collection.getIdAsInt());
        String url = metabaseApiUrl + "/collection/graph";
        sendPutRequest(url, request);
    }
}
