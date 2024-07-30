package org.avni.server.web.request.reports;

import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

public class ObservationBasedFilterRequest extends ObservationBasedFilterContract {
    private String conceptUUID;

    @Override
    public String getConceptUUID() {
        return conceptUUID;
    }

    public void setConceptUUID(String conceptUUID) {
        this.conceptUUID = conceptUUID;
    }
}
