package org.avni.server.web.request;

import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.contract.reports.DashboardFilterContract;
import org.avni.server.web.response.reports.DashboardFilterConfigResponse;

public class DashboardFilterResponse extends DashboardFilterContract {
    private DashboardFilterConfigResponse filterConfig;

    public DashboardFilterConfigResponse getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(DashboardFilterConfigResponse filterConfig) {
        this.filterConfig = filterConfig;
    }

    @Override
    public DashboardFilterConfigContract newFilterConfig() {
        this.filterConfig = new DashboardFilterConfigResponse();
        return this.filterConfig;
    }
}
