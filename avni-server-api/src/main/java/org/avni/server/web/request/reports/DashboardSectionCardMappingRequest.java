package org.avni.server.web.request.reports;

import org.avni.server.domain.DashboardSectionCardMapping;
import org.avni.server.web.request.DashboardSectionCardMappingContract;

public class DashboardSectionCardMappingRequest extends DashboardSectionCardMappingContract {
    private String reportCardUUID;

    public static DashboardSectionCardMappingRequest fromEntity(DashboardSectionCardMapping mapping) {
        DashboardSectionCardMappingRequest contract = new DashboardSectionCardMappingRequest();
        contract.setUuid(mapping.getUuid());
        contract.setReportCardUUID(mapping.getCard().getUuid());
        contract.setDisplayOrder(mapping.getDisplayOrder());
        contract.setVoided(mapping.isVoided());
        return contract;
    }

    public String getReportCardUUID() {
        return reportCardUUID;
    }

    public void setReportCardUUID(String reportCardUUID) {
        this.reportCardUUID = reportCardUUID;
    }
}
