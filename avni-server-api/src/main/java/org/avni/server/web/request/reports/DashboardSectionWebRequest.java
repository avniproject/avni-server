package org.avni.server.web.request.reports;

import org.avni.server.web.contract.reports.DashboardSectionContract;

import java.util.ArrayList;
import java.util.List;

public class DashboardSectionWebRequest extends DashboardSectionContract {
    private List<DashboardSectionCardMappingRequest> dashboardSectionCardMappings = new ArrayList<>();

    public List<DashboardSectionCardMappingRequest> getDashboardSectionCardMappings() {
        return dashboardSectionCardMappings;
    }

    public void setDashboardSectionCardMappings(List<DashboardSectionCardMappingRequest> dashboardSectionCardMappings) {
        this.dashboardSectionCardMappings = dashboardSectionCardMappings;
    }
}
