package org.avni.server.web.response.reports;

import org.avni.server.web.request.DashboardSectionCardMappingContract;

public class DashboardSectionCardMappingWebResponse extends DashboardSectionCardMappingContract {
    private ReportCardWebResponse card;

    public ReportCardWebResponse getCard() {
        return card;
    }

    public void setCard(ReportCardWebResponse card) {
        this.card = card;
    }
}
