package org.avni.server.mapper.dashboard;

import org.avni.server.domain.Dashboard;
import org.avni.server.web.request.DashboardResponse;
import org.avni.server.web.request.DashboardFilterResponse;
import org.avni.server.web.request.DashboardSectionContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DashboardMapper {
    private final DashboardFilterMapper dashboardFilterMapper;

    @Autowired
    public DashboardMapper(DashboardFilterMapper dashboardFilterMapper) {
        this.dashboardFilterMapper = dashboardFilterMapper;
    }

    public DashboardResponse fromEntity(Dashboard dashboard) {
        DashboardResponse dashboardResponse = new DashboardResponse();
        dashboardResponse.setId(dashboard.getId());
        dashboardResponse.setUuid(dashboard.getUuid());
        dashboardResponse.setVoided(dashboard.isVoided());
        dashboardResponse.setName(dashboard.getName());
        dashboardResponse.setDescription(dashboard.getDescription());
        setSections(dashboardResponse, dashboard);
        setFilters(dashboardResponse, dashboard);
        return dashboardResponse;
    }

    private void setSections(DashboardResponse dashboardContract, Dashboard dashboard) {
        List<DashboardSectionContract> list = dashboard.getDashboardSections()
                .stream()
                .map(DashboardSectionContract::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setSections(list);
    }

    private void setFilters(DashboardResponse dashboardContract, Dashboard dashboard) {
        List<DashboardFilterResponse> list = dashboard.getDashboardFilters()
                .stream()
                .map(dashboardFilterMapper::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setFilters(list);
    }
}
