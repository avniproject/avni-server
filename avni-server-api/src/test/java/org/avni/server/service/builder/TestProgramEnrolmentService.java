package org.avni.server.service.builder;

import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestProgramEnrolmentService {
    private final ProgramEnrolmentRepository programEnrolmentRepository;

    @Autowired
    public TestProgramEnrolmentService(ProgramEnrolmentRepository programEnrolmentRepository) {
        this.programEnrolmentRepository = programEnrolmentRepository;
    }

    public ProgramEnrolment save(ProgramEnrolment programEnrolment) {
        Individual individual = programEnrolment.getIndividual();
        if (individual.getObservations() != null) {
            programEnrolment.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        }
        programEnrolment.setAddressId(individual.getAddressLevel().getId());
        programEnrolmentRepository.save(programEnrolment);
        return programEnrolment;
    }

    public ProgramEnrolment reload(ProgramEnrolment enrolment) {
        return programEnrolmentRepository.findEntity(enrolment.getId());
    }
}
