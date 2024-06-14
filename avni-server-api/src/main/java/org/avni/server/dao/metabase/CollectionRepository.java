package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Collection;
import org.avni.server.domain.metabase.CollectionResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;

public class CollectionRepository extends MetabaseRepository{
    public CollectionRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public CollectionResponse save(Collection collection) {
        String url = metabaseApiUrl + "/collection";
        HttpEntity<Collection> entity = createHttpEntity(collection);
        CollectionResponse response = restTemplate.postForObject(url, entity, CollectionResponse.class);

        return response;
    }
}
