package org.avni.server.domain.factory.txData;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ObservationCollection;

public class ObservationCollectionBuilder {
    private final ObservationCollection observationCollection = new ObservationCollection();

    public ObservationCollection build() {
        return observationCollection;
    }

    public ObservationCollectionBuilder addObservation(String conceptUUID, Object value) {
        observationCollection.put(conceptUUID, value);
        return this;
    }

    public static ObservationCollection withOneObservation(Concept concept, Object value) {
        ObservationCollection observationCollection = new ObservationCollection();
        observationCollection.put(concept.getUuid(), value);
        return observationCollection;
    }
}
