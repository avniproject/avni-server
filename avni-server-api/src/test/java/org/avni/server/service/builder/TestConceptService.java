package org.avni.server.service.builder;

import org.avni.server.application.KeyValues;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.metadata.ConceptAnswerBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class TestConceptService {
    private final ConceptRepository conceptRepository;

    @Autowired
    public TestConceptService(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    public Concept createCodedConcept(String conceptName, String ... answerConceptNames) {
        Concept concept = new ConceptBuilder().withName(conceptName).withDataType(ConceptDataType.Coded).build();
        Arrays.stream(answerConceptNames).forEach(s -> {
            Concept answerConcept = new ConceptBuilder().withName(s).withDataType(ConceptDataType.NA).build();
            conceptRepository.save(answerConcept);
            concept.addAnswer(new ConceptAnswerBuilder().withConcept(concept).withAnswerConcept(answerConcept).build());
        });
        return conceptRepository.save(concept);
    }

    public Concept createConcept(String conceptName, ConceptDataType conceptDataType) {
        Concept concept = new ConceptBuilder().withName(conceptName).withDataType(conceptDataType).build();
        return conceptRepository.save(concept);
    }

    public Concept createConceptWithKeyValues(String conceptName, ConceptDataType conceptDataType, KeyValues keyValues) {
        Concept concept = new ConceptBuilder().withName(conceptName).withDataType(conceptDataType).withKeyValues(keyValues).build();
        return conceptRepository.save(concept);
    }

    public Concept createNumericConceptWithAbsolutes(String conceptName, Double lowAbsolute, Double highAbsolute) {
        Concept concept = new ConceptBuilder().withName(conceptName).withDataType(ConceptDataType.Numeric).build();
        concept.setLowAbsolute(lowAbsolute);
        concept.setHighAbsolute(highAbsolute);
        return conceptRepository.save(concept);
    }
}
