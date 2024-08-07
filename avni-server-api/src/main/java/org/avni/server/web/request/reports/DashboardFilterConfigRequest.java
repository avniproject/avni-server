package org.avni.server.web.request.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.web.contract.DashboardFilterConfigContract;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardFilterConfigRequest extends DashboardFilterConfigContract {
    private ObservationBasedFilterRequest observationBasedFilter;

    public ObservationBasedFilterRequest getObservationBasedFilter() {
        return observationBasedFilter;
    }

    public void setObservationBasedFilter(ObservationBasedFilterRequest observationBasedFilter) {
        this.observationBasedFilter = observationBasedFilter;
    }

    @Override
    protected Object getObservationTypeFilterJsonObject() {
        return observationBasedFilter.getJsonObject();
    }
}
