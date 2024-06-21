package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Collection;
import org.avni.server.domain.metabase.CollectionResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

@Repository
public class CollectionRepository extends MetabaseConnector {
    public CollectionRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public CollectionResponse save(Collection collection) {
        String url = metabaseApiUrl + "/collection";
        return postForObject(url, collection, CollectionResponse.class);
    }
}
