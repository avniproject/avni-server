package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.CollectionInfoResponse;
import org.avni.server.domain.metabase.CollectionItem;
import org.avni.server.domain.metabase.CollectionResponse;
import org.avni.server.domain.metabase.CreateCollectionRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.avni.server.util.ObjectMapperSingleton.getObjectMapper;

@Repository
public class CollectionRepository extends MetabaseConnector {
    public CollectionRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public CollectionResponse save(CreateCollectionRequest createCollectionRequest) {
        String url = metabaseApiUrl + "/collection";
        return postForObject(url, createCollectionRequest, CollectionResponse.class);
    }

    public List<CollectionItem> getExistingCollectionItems(int collectionId) {
        String url = metabaseApiUrl + "/collection/" + collectionId + "/items";
        String jsonResponse = getForObject(url, String.class);

        try {
            JsonNode rootNode = getObjectMapper().readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            List<CollectionItem> items = new ArrayList<>();
            for (JsonNode itemNode : dataArray) {
                CollectionItem item = new CollectionItem();
                item.setName(itemNode.get("name").asText());
                item.setId(itemNode.get("id").asInt());
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch collection items", e);
        }
    }

    public CollectionInfoResponse getCollection(String databaseName) {
        String url = metabaseApiUrl + "/collection";

        String jsonResponse = getForObject(url, String.class);
        try {
            List<CollectionInfoResponse> collections = getObjectMapper().readValue(jsonResponse, new TypeReference<>() {});
            return collections.stream()
                    .filter(collection -> collection.getName().equals(databaseName))
                    .findFirst()
                    .orElse(null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public CollectionInfoResponse getCollection(Organisation organisation) {
        return getCollection(organisation.getName());
    }

    public void delete(CollectionInfoResponse collection) {
        String url = metabaseApiUrl + "/collection/" + collection.getIdAsInt();
        HashMap<String, Object> map = new HashMap<>();
        map.put("archived", true);
        sendPutRequest(url, map);
    }
}
