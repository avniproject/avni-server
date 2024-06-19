package org.avni.server.web.request;

public abstract class DashboardSectionCardMappingContract extends CHSRequest {
    private Double displayOrder;

    public Double getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Double displayOrder) {
        this.displayOrder = displayOrder;
    }
}
