package org.avni.server.web.request;

public class DashboardFilterContract extends CHSRequest {
    private String name;
    private DashboardFilterConfigResponse config;

    public String getName() {
        return name;
    }

    public DashboardFilterConfigResponse getConfig() {
        return config;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setConfig(DashboardFilterConfigResponse config) {
        this.config = config;
    }
}
