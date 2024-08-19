package org.avni.server.web.response.reports;

import org.avni.server.web.contract.reports.ObservationBasedFilterContract;
import org.avni.server.web.request.ConceptContract;

public class ObservationBasedFilterBundleContract extends ObservationBasedFilterContract {
    private String conceptUUID;

    public String getConceptUUID() {
        return conceptUUID;
    }

    public void setConceptUUID(String conceptUUID) {
        this.conceptUUID = conceptUUID;
    }

    @Override
    public void setConcept(ConceptContract conceptContract) {
        this.conceptUUID = conceptContract.getUuid();
    }
}
