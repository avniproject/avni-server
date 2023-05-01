package org.avni.server.web.request.reports;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

public class ObservationBasedFilterRequest extends ObservationBasedFilterContract {
    private String conceptUUID;
    private String scope;

    public String getConceptUUID() {
        return conceptUUID;
    }

    public void setConceptUUID(String conceptUUID) {
        this.conceptUUID = conceptUUID;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public JsonObject getJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ConceptFieldName, conceptUUID);
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ScopeFieldName, scope);
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ProgramsFieldName, getProgramUUIDs());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.EncounterTypesFieldName, getEncounterTypeUUIDs());
        return jsonObject;
    }
}
