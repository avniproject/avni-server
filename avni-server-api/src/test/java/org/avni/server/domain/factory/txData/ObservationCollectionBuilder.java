package org.avni.server.domain.factory.txData;

import org.avni.server.domain.ObservationCollection;

public class ObservationCollectionBuilder {
    private final ObservationCollection observationCollection = new ObservationCollection();

    public ObservationCollection get() {
        return observationCollection;
    }

    public ObservationCollectionBuilder addObservation(String conceptName, Object value) {
        observationCollection.put(conceptName, value);
        return this;
    }
}
