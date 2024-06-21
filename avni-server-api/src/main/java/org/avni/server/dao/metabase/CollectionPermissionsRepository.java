package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.CollectionPermissionsService;
import org.avni.server.domain.metabase.CollectionPermissionsGraphResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class CollectionPermissionsRepository extends MetabaseConnector {

    public CollectionPermissionsRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public CollectionPermissionsGraphResponse getCollectionPermissionsGraph() {
        String url = metabaseApiUrl + "/collection/graph";
        return getForObject(url, CollectionPermissionsGraphResponse.class);
    }

    public void updateCollectionPermissions(CollectionPermissionsService collectionPermissionsService, int groupId, int collectionId) {
        collectionPermissionsService.updatePermissions(groupId, collectionId);
        String url = metabaseApiUrl + "/collection/graph";
        sendPutRequest(url, collectionPermissionsService.getUpdatedPermissionsGraph());
    }
}
