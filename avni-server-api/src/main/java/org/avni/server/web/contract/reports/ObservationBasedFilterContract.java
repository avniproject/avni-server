package org.avni.server.web.contract.reports;

import java.util.ArrayList;
import java.util.List;

public class ObservationBasedFilterContract {
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
}
