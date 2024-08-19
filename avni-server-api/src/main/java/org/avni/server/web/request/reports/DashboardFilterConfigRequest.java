package org.avni.server.web.request.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.JsonObject;
import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardFilterConfigRequest extends DashboardFilterConfigContract {
    private ObservationBasedFilterRequest observationBasedFilter;

    public ObservationBasedFilterRequest getObservationBasedFilter() {
        return observationBasedFilter;
    }

    public void setObservationBasedFilter(ObservationBasedFilterRequest observationBasedFilter) {
        this.observationBasedFilter = observationBasedFilter;
    }

    public JsonObject toJsonObject() {
        return super.toJsonObject(observationBasedFilter == null ? null : observationBasedFilter.getJsonObject(observationBasedFilter.getConceptUUID()));
    }

    @Override
    public ObservationBasedFilterContract newObservationBasedFilter() {
        throw new RuntimeException("Not applicable for DashboardFilterConfigRequest, as it constructed via de-serialisation only");
    }
}
