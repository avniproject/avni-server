package org.avni.server.web.response.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardFilterConfigResponse extends DashboardFilterConfigContract {
    private ObservationBasedFilterResponse observationBasedFilter;

    public ObservationBasedFilterResponse getObservationBasedFilter() {
        return observationBasedFilter;
    }

    public void setObservationBasedFilter(ObservationBasedFilterResponse observationBasedFilter) {
        this.observationBasedFilter = observationBasedFilter;
    }

    @Override
    public ObservationBasedFilterContract newObservationBasedFilter() {
        this.observationBasedFilter = new ObservationBasedFilterResponse();
        return this.observationBasedFilter;
    }
}
