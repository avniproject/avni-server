package org.avni.server.domain.metabase;

import java.util.List;

public class DashboardUpdateRequest {
    private final List<Dashcard> dashcards;
    private List<Parameters> parameters;

    public DashboardUpdateRequest(List<Dashcard> dashcards) {
        this.dashcards = dashcards;
    }

    public DashboardUpdateRequest(List<Dashcard> dashcards, List<Parameters> parameters) {
        this.dashcards = dashcards;
        this.parameters = parameters;
    }

    public List<Dashcard> getDashcards() {
        return dashcards;
    }

    public List<Parameters> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "{" +
                "dashcards:" + dashcards +
                ", parameters:" + parameters +
                '}';
    }
}
