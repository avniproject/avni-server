package org.avni.server.web.request;

import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.app.dashboard.DashboardFilter;

public class DashboardFilterContract extends CHSRequest {
    private String name;
    private ObservationCollection filter;
    private String dashboardUUID;

    public static DashboardFilterContract fromEntity(DashboardFilter df) {
        DashboardFilterContract dashboardFilterContract = new DashboardFilterContract();
        dashboardFilterContract.setId(df.getId());
        dashboardFilterContract.setUuid(df.getUuid());
        dashboardFilterContract.setVoided(df.isVoided());
        dashboardFilterContract.setFilter(df.getFilter());
        dashboardFilterContract.setName(df.getName());
        dashboardFilterContract.setDashboardUUID(df.getDashboard().getUuid());
        return dashboardFilterContract;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObservationCollection getFilter() {
        return filter;
    }

    public void setFilter(ObservationCollection filter) {
        this.filter = filter;
    }

    public String getDashboardUUID() {
        return dashboardUUID;
    }

    public void setDashboardUUID(String dashboardUUID) {
        this.dashboardUUID = dashboardUUID;
    }
}
