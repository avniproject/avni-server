package org.avni.server.mapper.dashboard;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.service.MetaDataRepository;
import org.avni.server.web.request.DashboardContract;
import org.avni.server.web.request.DashboardFilterContract;
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

    public DashboardContract fromEntity(Dashboard dashboard) {
        DashboardContract dashboardContract = new DashboardContract();
        dashboardContract.setId(dashboard.getId());
        dashboardContract.setUuid(dashboard.getUuid());
        dashboardContract.setVoided(dashboard.isVoided());
        dashboardContract.setName(dashboard.getName());
        dashboardContract.setDescription(dashboard.getDescription());
        setSections(dashboardContract, dashboard);
        setFilters(dashboardContract, dashboard);
        return dashboardContract;
    }

    private void setSections(DashboardContract dashboardContract, Dashboard dashboard) {
        List<DashboardSectionContract> list = dashboard.getDashboardSections()
                .stream()
                .filter(it -> !it.isVoided())
                .map(DashboardSectionContract::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setSections(list);
    }

    private void setFilters(DashboardContract dashboardContract, Dashboard dashboard) {
        List<DashboardFilterContract> list = dashboard.getDashboardFilters()
                .stream()
                .filter(it -> !it.isVoided())
                .map(dashboardFilterMapper::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setFilters(list);
    }
}
