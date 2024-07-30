package org.avni.server.web.contract.reports;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;

import java.util.ArrayList;
import java.util.List;

public abstract class ObservationBasedFilterContract {
    private String scope;
    private List<String> programUUIDs = new ArrayList<>();
    private List<String> encounterTypeUUIDs = new ArrayList<>();

    public List<String> getProgramUUIDs() {
        return programUUIDs;
    }

    public void setProgramUUIDs(List<String> programUUIDs) {
        this.programUUIDs = programUUIDs;
    }

    public List<String> getEncounterTypeUUIDs() {
        return encounterTypeUUIDs;
    }

    public void setEncounterTypeUUIDs(List<String> encounterTypeUUIDs) {
        this.encounterTypeUUIDs = encounterTypeUUIDs;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public JsonObject getJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ConceptFieldName, this.getConceptUUID());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ScopeFieldName, this.getScope());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ProgramsFieldName, getProgramUUIDs());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.EncounterTypesFieldName, getEncounterTypeUUIDs());
        return jsonObject;
    }

    protected abstract String getConceptUUID();
}
