package org.avni.server.web.response.reports;

import org.avni.server.web.contract.reports.DashboardSectionContract;

import java.util.ArrayList;
import java.util.List;

public class DashboardSectionWebResponse extends DashboardSectionContract {
    private List<DashboardSectionCardMappingWebResponse> dashboardSectionCardMappings = new ArrayList<>();

    public List<DashboardSectionCardMappingWebResponse> getDashboardSectionCardMappings() {
        return dashboardSectionCardMappings;
    }

    public void setDashboardSectionCardMappings(List<DashboardSectionCardMappingWebResponse> dashboardSectionCardMappings) {
        this.dashboardSectionCardMappings = dashboardSectionCardMappings;
    }
}
