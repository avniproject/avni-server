package org.avni.server.web.request.reports;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

public class ObservationBasedFilterRequest extends ObservationBasedFilterContract {
    private String concept;

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public JsonObject getJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ConceptFieldName, concept);
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ProgramsFieldName, getProgramUUIDs());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.EncounterTypesFieldName, getEncounterTypeUUIDs());
        return jsonObject;
    }
}
