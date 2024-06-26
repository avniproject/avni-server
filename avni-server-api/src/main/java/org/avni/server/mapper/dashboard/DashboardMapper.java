package org.avni.server.mapper.dashboard;

import org.avni.server.domain.Dashboard;
import org.avni.server.domain.DashboardSection;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionCardMappingBundleContract;
import org.avni.server.web.request.*;
import org.avni.server.web.response.reports.DashboardSectionCardMappingWebResponse;
import org.avni.server.web.response.reports.DashboardSectionWebResponse;
import org.avni.server.web.response.reports.DashboardWebResponse;
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

    public DashboardWebResponse toWebResponse(Dashboard dashboard) {
        DashboardWebResponse dashboardResponse = new DashboardWebResponse();
        dashboardResponse.populatePrimitives(dashboard);
        setSections(dashboardResponse, dashboard);
        setFilters(dashboardResponse, dashboard);
        return dashboardResponse;
    }

    private void setSections(DashboardWebResponse dashboardContract, Dashboard dashboard) {
        List<DashboardSectionWebResponse> list = dashboard.getDashboardSections()
                .stream()
                .filter(dashboardSection -> !dashboardSection.isVoided())
                .map(this::toWebResponse)
                .collect(Collectors.toList());
        dashboardContract.setSections(list);
    }

    private void setSections(DashboardBundleContract dashboardContract, Dashboard dashboard) {
        List<DashboardSectionBundleContract> list = dashboard.getDashboardSections()
                .stream()
                .map(this::toBundle)
                .collect(Collectors.toList());
        dashboardContract.setSections(list);
    }

    private void setFilters(DashboardWebResponse dashboardContract, Dashboard dashboard) {
        List<DashboardFilterResponse> list = dashboard.getDashboardFilters()
                .stream()
                .filter(dashboardFilter -> !dashboardFilter.isVoided())
                .map(dashboardFilterMapper::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setFilters(list);
    }

    private void setFilters(DashboardBundleContract dashboardContract, Dashboard dashboard) {
        List<DashboardFilterResponse> list = dashboard.getDashboardFilters()
                .stream()
                .map(dashboardFilterMapper::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setFilters(list);
    }

    private DashboardSectionWebResponse toWebResponse(DashboardSection ds) {
        DashboardSectionWebResponse dashboardContract = new DashboardSectionWebResponse();
        dashboardContract.setPrimitiveFields(ds);
        setDashboardSectionCardMappings(dashboardContract, ds);
        return dashboardContract;
    }

    private DashboardSectionBundleContract toBundle(DashboardSection ds) {
        DashboardSectionBundleContract dashboardContract = new DashboardSectionBundleContract();
        dashboardContract.setPrimitiveFields(ds);
        setDashboardSectionCardMappings(dashboardContract, ds);
        return dashboardContract;
    }

    private void setDashboardSectionCardMappings(DashboardSectionWebResponse response, DashboardSection ds) {
        List<DashboardSectionCardMappingWebResponse> mappingContracts = ds.getDashboardSectionCardMappings().stream()
                .map(dashboardSectionCardMapping -> {
                    DashboardSectionCardMappingWebResponse mappingResponse = new DashboardSectionCardMappingWebResponse();
                    mappingResponse.setUuid(dashboardSectionCardMapping.getUuid());
                    mappingResponse.setDisplayOrder(dashboardSectionCardMapping.getDisplayOrder());
                    mappingResponse.setCard(reportCardMapper.toWebResponse(dashboardSectionCardMapping.getCard()));
                    mappingResponse.setVoided(dashboardSectionCardMapping.isVoided());
                    return mappingResponse;
                })
                .collect(Collectors.toList());
        response.setDashboardSectionCardMappings(mappingContracts);
    }

    private void setDashboardSectionCardMappings(DashboardSectionBundleContract contract, DashboardSection dashboardSection) {
        List<DashboardSectionCardMappingBundleContract> mappingContracts = dashboardSection.getDashboardSectionCardMappings().stream()
                .map(DashboardSectionCardMappingBundleContract::fromEntity)
                .collect(Collectors.toList());
        contract.setDashboardSectionCardMappings(mappingContracts);
    }

    public DashboardBundleContract toBundle(Dashboard dashboard) {
        DashboardBundleContract bundleContract = new DashboardBundleContract();
        bundleContract.populatePrimitives(dashboard);
        setSections(bundleContract, dashboard);
        setFilters(bundleContract, dashboard);
        return bundleContract;
    }
}
