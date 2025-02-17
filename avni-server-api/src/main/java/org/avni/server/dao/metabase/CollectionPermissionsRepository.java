package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.CollectionInfoResponse;
import org.avni.server.domain.metabase.Group;
import org.avni.server.domain.metabase.MetabaseRequestFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class CollectionPermissionsRepository extends MetabaseConnector {
    public CollectionPermissionsRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public void updateCollectionPermissions(Group group, CollectionInfoResponse collection) {
        String url = metabaseApiUrl + "/collection/graph";

        Map<String, Object> collectionPermissionsGraph = getMapResponse(url);
        Map<String, Object> request = MetabaseRequestFactory.deriveRequestToUpdateCollectionPermissions(collectionPermissionsGraph, collection.getIdAsInt(), group);
        sendPutRequest(url, request);
    }
}
