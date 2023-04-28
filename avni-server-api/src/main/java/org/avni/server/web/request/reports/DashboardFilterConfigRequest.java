package org.avni.server.web.request.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
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

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        DashboardFilter.FilterType filterType = DashboardFilter.FilterType.valueOf(this.getType());
        jsonObject.with(DashboardFilter.DashboardFilterConfig.TypeFieldName, this.getType())
                .with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, this.getSubjectTypeUUID())
                .with(DashboardFilter.DashboardFilterConfig.WidgetFieldName, this.getWidget());
        if (filterType.equals(DashboardFilter.FilterType.GroupSubject))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.ScopeFieldName, getGroupSubjectTypeScope().getJsonObject());
        else if (filterType.equals(DashboardFilter.FilterType.Concept))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.ScopeFieldName, observationBasedFilter.getJsonObject());
        return jsonObject;
    }
}
