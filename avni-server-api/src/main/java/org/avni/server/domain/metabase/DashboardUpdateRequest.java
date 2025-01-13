package org.avni.server.domain.metabase;

import java.util.List;

public class DashboardUpdateRequest {
    private final List<Dashcard> dashcards;

    public DashboardUpdateRequest(List<Dashcard> dashcards) {
        this.dashcards = dashcards;
    }


    public List<Dashcard> getDashcards() {
        return dashcards;
    }
}
