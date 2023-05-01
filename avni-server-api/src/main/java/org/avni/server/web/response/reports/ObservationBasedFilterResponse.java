package org.avni.server.web.response.reports;

import org.avni.server.web.contract.reports.ObservationBasedFilterContract;
import org.avni.server.web.request.ConceptContract;

public class ObservationBasedFilterResponse extends ObservationBasedFilterContract {
    private ConceptContract concept;
    private String scope;

    public ConceptContract getConcept() {
        return concept;
    }

    public void setConcept(ConceptContract concept) {
        this.concept = concept;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
