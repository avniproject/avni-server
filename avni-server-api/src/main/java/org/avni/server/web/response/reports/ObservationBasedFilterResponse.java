package org.avni.server.web.response.reports;

import org.avni.server.web.contract.reports.ObservationBasedFilterContract;
import org.avni.server.web.request.ConceptContract;

public class ObservationBasedFilterResponse extends ObservationBasedFilterContract {
    private ConceptContract concept;

    public ConceptContract getConcept() {
        return concept;
    }

    public void setConcept(ConceptContract concept) {
        this.concept = concept;
    }
}
