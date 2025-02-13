package org.avni.server.domain.metabase;

import java.util.List;

public class DashboardUpdateRequest {
    private final List<Dashcard> dashcards;
    private List<Parameters> parameters;
    private List<Tabs> tabs;

    public DashboardUpdateRequest(List<Dashcard> dashcards) {
        this.dashcards = dashcards;
    }

    public DashboardUpdateRequest(List<Dashcard> dashcards, List<Parameters> parameters, List<Tabs> tabs) {
        this.dashcards = dashcards;
        this.parameters = parameters;
        this.tabs = tabs;
    }

    public List<Dashcard> getDashcards() {
        return dashcards;
    }

    public List<Parameters> getParameters() {
        return parameters;
    }

    public List<Tabs> getTabs() {
        return tabs;
    }

    @Override
    public String toString() {
        return "{" +
                "dashcards:" + dashcards +
                ", parameters:" + parameters +
                '}';
    }
}
