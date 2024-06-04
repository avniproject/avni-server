package org.avni.server.web.contract.reports;

import org.avni.server.domain.DashboardSectionCardMapping;
import org.avni.server.web.request.DashboardSectionCardMappingContract;

public class DashboardSectionCardMappingBundleContract extends DashboardSectionCardMappingContract {
    private String dashboardSectionUUID;
    private String reportCardUUID;

    public static DashboardSectionCardMappingBundleContract fromEntity(DashboardSectionCardMapping mapping) {
        DashboardSectionCardMappingBundleContract contract = new DashboardSectionCardMappingBundleContract();
        contract.setUuid(mapping.getUuid());
        contract.setDashboardSectionUUID(mapping.getDashboardSection().getUuid());
        contract.setReportCardUUID(mapping.getCard().getUuid());
        contract.setDisplayOrder(mapping.getDisplayOrder());
        contract.setVoided(mapping.isVoided());
        return contract;
    }

    public String getDashboardSectionUUID() {
        return dashboardSectionUUID;
    }

    public void setDashboardSectionUUID(String dashboardSectionUUID) {
        this.dashboardSectionUUID = dashboardSectionUUID;
    }

    public String getReportCardUUID() {
        return reportCardUUID;
    }

    public void setReportCardUUID(String reportCardUUID) {
        this.reportCardUUID = reportCardUUID;
    }
}
