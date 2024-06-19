package org.avni.server.web.contract.reports;

import org.avni.server.domain.DashboardSection;
import org.avni.server.web.request.CHSRequest;

public abstract class DashboardSectionContract extends CHSRequest {
    private String name;
    private String description;
    private String viewType;
    private Double displayOrder;
    private String dashboardUUID;

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

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public Double getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Double displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getDashboardUUID() {
        return dashboardUUID;
    }

    public void setDashboardUUID(String dashboardUUID) {
        this.dashboardUUID = dashboardUUID;
    }

    public void setPrimitiveFields(DashboardSection dashboardSection) {
        this.setId(dashboardSection.getId());
        this.setUuid(dashboardSection.getUuid());
        this.setVoided(dashboardSection.isVoided());
        this.setName(dashboardSection.getName());
        this.setDescription(dashboardSection.getDescription());
        this.setViewType(dashboardSection.getViewType().name());
        this.setDisplayOrder(dashboardSection.getDisplayOrder());
        this.setDashboardUUID(dashboardSection.getDashboardUUID());
    }
}
