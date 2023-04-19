package org.avni.server.service;

import org.avni.server.dao.DashboardFilterRepository;
import org.avni.server.dao.DashboardSectionRepository;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Service
public class DashboardFilterService implements NonScopeAwareService {
    private final DashboardFilterRepository dashboardFilterRepository;

    public DashboardFilterService(DashboardFilterRepository dashboardFilterRepository) {
        this.dashboardFilterRepository = dashboardFilterRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return dashboardFilterRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}
