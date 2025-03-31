package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MetabaseDashboardRepository extends MetabaseConnector {
    private final CollectionRepository collectionRepository;
    private static final Logger logger = LoggerFactory.getLogger(MetabaseDashboardRepository.class);

    public MetabaseDashboardRepository(RestTemplateBuilder restTemplateBuilder , CollectionRepository collectionRepository) {
        super(restTemplateBuilder);
        this.collectionRepository = collectionRepository;
    }

    public Dashboard save(CreateDashboardRequest createDashboardRequest) {
        String url = metabaseApiUrl + "/dashboard";
        try {
            return postForObject(url, createDashboardRequest, Dashboard.class);
        } catch (RuntimeException e) {
            logger.error("Save dashboard failed for: {}", url);
            throw e;
        } catch (Exception e) {
            logger.error("Save dashboard failed for: {}", url);
            throw new RuntimeException(e);
        }
    }

    public CollectionItem getDashboard(CollectionInfoResponse collection) {
        try {
            List<CollectionItem> items = collectionRepository.getExistingCollectionItems(collection.getIdAsInt());
            return items.stream()
                    .filter(item -> item.getName().equals(DashboardName.CANNED_REPORTS.getName()))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            logger.error("Get dashboard failed for collection: {}", collection.getId());
            throw e;
        } catch (Exception e) {
            logger.error("Get dashboard failed for collection: {}", collection.getId());
            throw new RuntimeException(e);
        }
    }

    public void updateDashboard(int dashboardId, DashboardUpdateRequest request) {
        String url = metabaseApiUrl + "/dashboard/" + dashboardId;
        try {
            sendPutRequest(url, request);
        } catch (RuntimeException e) {
            logger.error("Update dashboard failed for: {}", url);
            throw e;
        } catch (Exception e) {
            logger.error("Update dashboard failed for: {}", url);
            throw new RuntimeException(e);
        }
    }
}
