package org.avni.server.web.contract.reports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.Concept;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.request.ConceptContract;

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

    @JsonIgnore
    public JsonObject getJsonObject(String conceptUUID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ConceptFieldName, conceptUUID);
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ScopeFieldName, this.getScope());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.ProgramsFieldName, getProgramUUIDs());
        jsonObject.put(DashboardFilter.ObservationBasedFilter.EncounterTypesFieldName, getEncounterTypeUUIDs());
        return jsonObject;
    }

    public abstract void setConcept(ConceptContract conceptContract);
}
