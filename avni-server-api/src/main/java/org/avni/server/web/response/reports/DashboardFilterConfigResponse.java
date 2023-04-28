package org.avni.server.web.response.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.DashboardFilterConfigContract;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardFilterConfigResponse extends DashboardFilterConfigContract {
    private ObservationBasedFilterResponse conceptScope;

    public ObservationBasedFilterResponse getConceptScope() {
        return conceptScope;
    }

    public void setConceptScope(ObservationBasedFilterResponse conceptScope) {
        this.conceptScope = conceptScope;
    }
}
