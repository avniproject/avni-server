package org.avni.server.domain.factory.txData;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ObservationCollection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ObservationCollectionBuilder {
    private final ObservationCollection observationCollection = new ObservationCollection();

    public ObservationCollection build() {
        return observationCollection;
    }

    public ObservationCollectionBuilder addObservation(Concept concept, Concept value) {
        addObservation(concept.getUuid(), value.getUuid());
        return this;
    }

    public ObservationCollectionBuilder addMultiCodedObservation(Concept concept, Concept... values) {
        String answer = Arrays.asList(values).stream().map(value -> value.getUuid()).collect(Collectors.joining(","));
        addObservation(concept.getUuid(), Arrays.stream(values).map(item -> item.getUuid()).collect(Collectors.toList()));
        return this;
    }

    public ObservationCollectionBuilder addObservation(Concept concept, Object value) {
        addObservation(concept.getUuid(), value);
        return this;
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
