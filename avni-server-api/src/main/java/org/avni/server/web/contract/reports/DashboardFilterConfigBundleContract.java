package org.avni.server.web.contract.reports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.JsonObject;
import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.response.reports.ObservationBasedFilterBundleContract;

public class DashboardFilterConfigBundleContract extends DashboardFilterConfigContract {
    private ObservationBasedFilterBundleContract observationBasedFilter;

    public ObservationBasedFilterBundleContract getObservationBasedFilter() {
        return observationBasedFilter;
    }

    public void setObservationBasedFilter(ObservationBasedFilterBundleContract observationBasedFilter) {
        this.observationBasedFilter = observationBasedFilter;
    }

    @JsonIgnore
    public JsonObject toJsonObject() {
        return super.toJsonObject(observationBasedFilter == null ? null : observationBasedFilter.getJsonObject(observationBasedFilter.getConceptUUID()));
    }

    @Override
    public ObservationBasedFilterContract newObservationBasedFilter() {
        this.observationBasedFilter = new ObservationBasedFilterBundleContract();
        return this.observationBasedFilter;
    }
}
