package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.CreateCollectionRequest;
import org.avni.server.domain.metabase.CollectionResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

@Repository
public class CollectionRepository extends MetabaseConnector {
    public CollectionRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public CollectionResponse save(CreateCollectionRequest createCollectionRequest) {
        String url = metabaseApiUrl + "/collection";
        return postForObject(url, createCollectionRequest, CollectionResponse.class);
    }
}
