package org.avni.server.web.contract.reports;

import org.avni.server.domain.Dashboard;
import org.avni.server.web.request.CHSRequest;

public class DashboardContract extends CHSRequest {
    private String name;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void populatePrimitives(Dashboard dashboard) {
        this.setId(dashboard.getId());
        this.setUuid(dashboard.getUuid());
        this.setVoided(dashboard.isVoided());
        this.setName(dashboard.getName());
        this.setDescription(dashboard.getDescription());
    }
}
