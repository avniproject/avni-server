package org.avni.server.domain.factory.metadata;

import org.avni.server.application.KeyValues;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;

import java.util.Arrays;
import java.util.UUID;

public class ConceptBuilder {
    private final Concept concept = new Concept();

    public ConceptBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public ConceptBuilder withUuid(String uuid) {
        concept.setUuid(uuid);
        return this;
    }

    public ConceptBuilder withAnswers(ConceptAnswer ... conceptAnswers) {
        Arrays.stream(conceptAnswers).forEach(concept::addAnswer);
        return this;
    }

    public ConceptBuilder withName(String conceptName) {
        concept.setName(conceptName);
        return this;
    }

    public Concept build() {
        return concept;
    }

    public ConceptBuilder withDataType(ConceptDataType conceptDataType) {
        concept.setDataType(conceptDataType.toString());
        return this;
    }

    public ConceptBuilder withId(long id) {
        concept.setId(id);
        return this;
    }

    public ConceptBuilder withKeyValues(KeyValues keyValues) {
        concept.setKeyValues(keyValues);
        return this;
    }
}
