package org.avni.server.web.request;

import org.avni.server.domain.Dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardContract extends CHSRequest {

    private String name;
    private String description;
    private List<DashboardSectionContract> sections = new ArrayList<>();
    private List<DashboardFilterContract> filters = new ArrayList<>();

    public static DashboardContract fromEntity(Dashboard dashboard) {
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

    private static void setSections(DashboardContract dashboardContract, Dashboard dashboard) {
        List<DashboardSectionContract> list = dashboard.getDashboardSections()
                .stream()
                .filter(it -> !it.isVoided())
                .map(DashboardSectionContract::fromEntity)
                .collect(Collectors.toList());
        dashboardContract.setSections(list);
    }

    private static void setFilters(DashboardContract dashboardContract, Dashboard dashboard) {
        List<DashboardFilterContract> list = dashboard.getDashboardFilters()
            .stream()
            .filter(it -> !it.isVoided())
            .map(DashboardFilterContract::fromEntity)
            .collect(Collectors.toList());
        dashboardContract.setFilters(list);
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<DashboardSectionContract> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionContract> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterContract> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterContract> filters) {
        this.filters = filters;
    }
}
