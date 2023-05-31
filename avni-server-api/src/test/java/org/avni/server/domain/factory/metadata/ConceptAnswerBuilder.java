package org.avni.server.domain.factory.metadata;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;

public class ConceptAnswerBuilder {
    private final ConceptAnswer conceptAnswer = new ConceptAnswer();

    public ConceptAnswerBuilder withConcept(Concept concept) {
        conceptAnswer.setConcept(concept);
        return this;
    }

    public ConceptAnswerBuilder withAnswerConcept(Concept answerConcept) {
        conceptAnswer.setAnswerConcept(answerConcept);
        return this;
    }

    public ConceptAnswerBuilder withOrder(double order) {
        conceptAnswer.setOrder(order);
    	return this;
    }

    public ConceptAnswerBuilder withUUID(String uuid) {
        conceptAnswer.setUuid(uuid);
    	return this;
    }

    public ConceptAnswer build() {
        return conceptAnswer;
    }
}
