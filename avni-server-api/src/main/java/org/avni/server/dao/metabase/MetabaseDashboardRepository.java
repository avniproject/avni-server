package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MetabaseDashboardRepository extends MetabaseConnector {
    private final CollectionRepository collectionRepository;

    public MetabaseDashboardRepository(RestTemplateBuilder restTemplateBuilder , CollectionRepository collectionRepository) {
        super(restTemplateBuilder);
        this.collectionRepository = collectionRepository;
    }

    public Dashboard save(CreateDashboardRequest createDashboardRequest) {
        String url = metabaseApiUrl + "/dashboard";
        return postForObject(url, createDashboardRequest, Dashboard.class);
    }

    public CollectionItem getDashboard(CollectionInfoResponse collection) {
        List<CollectionItem> items = collectionRepository.getExistingCollectionItems(collection.getIdAsInt());

        return items.stream()
                .filter(item -> item.getName().equals(DashboardName.CANNED_REPORTS.getName()))
                .findFirst()
                .orElse(null);
    }

    public void updateDashboard(int dashboardId, DashboardUpdateRequest request) {
        String url = metabaseApiUrl + "/dashboard/" + dashboardId;
        sendPutRequest(url, request);
    }
}
