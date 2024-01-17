package org.avni.server.service.builder;

import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.Individual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestSubjectService {
    private final IndividualRepository individualRepository;

    @Autowired
    public TestSubjectService(IndividualRepository individualRepository) {
        this.individualRepository = individualRepository;
    }

    public Individual save(Individual individual) {
        if (individual.getObservations() != null) {
            individual.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        }
        individualRepository.save(individual);
        return individual;
    }

    public Individual reload(Individual subject) {
        return individualRepository.findEntity(subject.getId());
    }
}
