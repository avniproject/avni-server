package org.avni.server.domain.metabase;

public enum DashboardName {
    CANNED_REPORTS("Canned Reports");
    private final String name;
    DashboardName(String name) {
        this.name=name;
    }

    public String getName() {
        return name;
    }
}
