package org.openchs.dao;

import org.openchs.domain.Concept;
import org.openchs.domain.ConceptAnswer;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "conceptAnswer", path = "conceptAnswer")
public interface ConceptAnswerRepository extends ReferenceDataRepository<ConceptAnswer>, FindByLastModifiedDateTime<ConceptAnswer> {
    ConceptAnswer findByConceptAndAnswerConcept(Concept concept, Concept answerConcept);

    default ConceptAnswer findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in ConceptAnswer");
    }

    default ConceptAnswer findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in ConceptAnswer");
    }

    List<ConceptAnswer> findByOrganisationId(Long organisationId);
}