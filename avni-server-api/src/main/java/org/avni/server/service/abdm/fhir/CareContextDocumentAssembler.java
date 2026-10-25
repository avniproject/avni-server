package org.avni.server.service.abdm.fhir;

import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// An Avni encounter and its patient as a CareContextDocument. Call inside a transaction (lazy observations, concepts).
@Component
public class CareContextDocumentAssembler {
    public CareContextDocument assemble(Encounter encounter, Individual individual, String abhaNumber, String abhaAddress,
                                        String hipId, String hipName) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
