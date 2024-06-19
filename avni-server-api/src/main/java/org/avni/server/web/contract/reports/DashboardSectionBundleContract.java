package org.avni.server.web.contract.reports;

import java.util.ArrayList;
import java.util.List;

public class DashboardSectionBundleContract extends DashboardSectionContract {
    private List<DashboardSectionCardMappingBundleContract> dashboardSectionCardMappings = new ArrayList<>();

    public List<DashboardSectionCardMappingBundleContract> getDashboardSectionCardMappings() {
        return dashboardSectionCardMappings;
    }

    public void setDashboardSectionCardMappings(List<DashboardSectionCardMappingBundleContract> dashboardSectionCardMappings) {
        this.dashboardSectionCardMappings = dashboardSectionCardMappings;
    }
}
