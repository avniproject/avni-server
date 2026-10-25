package org.avni.server.service.abdm.fhir;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// A care context as an ABDM OPConsultation FHIR R4 document bundle, built with Jackson (no HAPI).
@Component
public class FhirBundleBuilder {
    public ObjectNode build(CareContextDocument document) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
