package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class MetabaseDashboardRepository extends MetabaseConnector {
    private final DatabaseRepository databaseRepository;

    public MetabaseDashboardRepository(RestTemplateBuilder restTemplateBuilder , DatabaseRepository databaseRepository) {
        super(restTemplateBuilder);
        this.databaseRepository = databaseRepository;
    }

    public Dashboard save(CreateDashboardRequest createDashboardRequest) {
        String url = metabaseApiUrl + "/dashboard";
        return postForObject(url, createDashboardRequest, Dashboard.class);
    }


    public CollectionItem getDashboardByName(CollectionInfoResponse globalCollection) {
        List<CollectionItem> items = databaseRepository.getExistingCollectionItems(globalCollection.getIdAsInt());

        return items.stream()
                .filter(item -> item.getName().equals(globalCollection.getName()))
                .findFirst()
                .orElse(null);
    }

    public void updateDashboard(int dashboardId, DashboardUpdateRequest request) {
        String url = metabaseApiUrl + "/dashboard/" + dashboardId;
        sendPutRequest(url, request);
    }
}
