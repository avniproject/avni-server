package org.avni.server.web.contract.reports;

import org.avni.server.web.request.CHSRequest;

public class DashboardFilterContract  extends CHSRequest {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
