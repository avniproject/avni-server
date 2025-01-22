package org.avni.ruleServer;

import org.avni.server.dao.DashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RuleService {
    private final DashboardRepository dashboardRepository;

    @Autowired
    public RuleService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public void getDashboardReportCardsCount() {
        dashboardRepository.getAllNames();
    }
}
