package org.avni.server.web.contract.reports;

import org.avni.server.web.contract.DashboardFilterConfigContract;

public class DashboardFilterBundleContract extends DashboardFilterContract {
    private DashboardFilterConfigBundleContract filterConfig;

    public DashboardFilterConfigBundleContract getFilterConfig() {
        return filterConfig;
    }

    @Override
    public DashboardFilterConfigContract newFilterConfig() {
        this.filterConfig = new DashboardFilterConfigBundleContract();
        return this.filterConfig;
    }
}
