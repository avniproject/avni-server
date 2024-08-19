package org.avni.server.web.request;

import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.contract.reports.DashboardFilterContract;
import org.avni.server.web.request.reports.DashboardFilterConfigRequest;

public class DashboardFilterRequest extends DashboardFilterContract {
    private DashboardFilterConfigRequest filterConfig;

    public DashboardFilterConfigRequest getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(DashboardFilterConfigRequest filterConfig) {
        this.filterConfig = filterConfig;
    }

    @Override
    public DashboardFilterConfigContract newFilterConfig() {
        throw new RuntimeException("Not applicable for DashboardFilterRequest, as it constructed via de-serialisation only");
    }
}
