package org.avni.server.mapper.dashboard;

import org.avni.server.domain.Dashboard;
import org.avni.server.domain.DashboardSection;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DashboardMapper {
    private final DashboardFilterMapper dashboardFilterMapper;
    private final ReportCardMapper reportCardMapper;

    @Autowired
    public DashboardMapper(DashboardFilterMapper dashboardFilterMapper, ReportCardMapper reportCardMapper) {
        this.dashboardFilterMapper = dashboardFilterMapper;
        this.reportCardMapper = reportCardMapper;
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
                .map(this::fromEntity)
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

    private DashboardSectionContract fromEntity(DashboardSection ds) {
        DashboardSectionContract dashboardContract = new DashboardSectionContract();
        dashboardContract.setId(ds.getId());
        dashboardContract.setUuid(ds.getUuid());
        dashboardContract.setVoided(ds.isVoided());
        dashboardContract.setName(ds.getName());
        dashboardContract.setDescription(ds.getDescription());
        dashboardContract.setViewType(ds.getViewType().name());
        dashboardContract.setDisplayOrder(ds.getDisplayOrder());

        List<ReportCardContract> list = ds.getDashboardSectionCardMappings().stream()
                .map(mapping -> {
                    ReportCardContract cardContract = reportCardMapper.fromEntity(mapping.getCard());
                    cardContract.setDisplayOrder(mapping.getDisplayOrder());
                    return cardContract;
                })
                .collect(Collectors.toList());
        dashboardContract.setCards(list);

        setDashboardSectionCardMappings(dashboardContract, ds);
        dashboardContract.setDashboardUUID(ds.getDashboardUUID());
        return dashboardContract;
    }

    private void setDashboardSectionCardMappings(DashboardSectionContract contract, DashboardSection ds) {
        List<DashboardSectionCardMappingContract> mappingContracts = ds.getDashboardSectionCardMappings().stream()
                .map(DashboardSectionCardMappingContract ::fromEntity)
                .collect(Collectors.toList());
        contract.setDashboardSectionCardMappings(mappingContracts);
    }
}
