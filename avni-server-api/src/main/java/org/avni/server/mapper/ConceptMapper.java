package org.avni.server.mapper;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.web.request.ConceptContract;

import java.util.ArrayList;

public class ConceptMapper {
    public ConceptContract toConceptContract(Concept concept) {
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setId(concept.getId());
        conceptContract.setName(concept.getName());
        conceptContract.setUuid(concept.getUuid());
        conceptContract.setDataType(concept.getDataType());
        conceptContract.setLowAbsolute(concept.getLowAbsolute());
        conceptContract.setHighAbsolute(concept.getHighAbsolute());
        conceptContract.setLowNormal(concept.getLowNormal());
        conceptContract.setHighNormal(concept.getHighNormal());
        conceptContract.setUnit(concept.getUnit());
        conceptContract.setVoided(concept.isVoided());

        if (dataTypeMatches(ConceptDataType.Coded, concept)) {
            conceptContract.setAnswers(new ArrayList<>());
            for (ConceptAnswer answer : concept.getConceptAnswers()) {
                Concept answerConcept = answer.getAnswerConcept();

                ConceptContract answerConceptContract = new ConceptContract();
                answerConceptContract.setUuid(answerConcept.getUuid());
                answerConceptContract.setName(answerConcept.getName());
                answerConceptContract.setOrder(answer.getOrder());
                answerConceptContract.setAbnormal(answer.isAbnormal());
                answerConceptContract.setUnique(answer.isUnique());

                conceptContract.getAnswers().add(answerConceptContract);
            }
        }
        return conceptContract;
    }

    private boolean dataTypeMatches(ConceptDataType conceptDataType, Concept concept) {
        return ConceptDataType.matches(conceptDataType, concept.getDataType());
    }
}
