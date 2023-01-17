package org.avni.server.domain.factory.txn;

import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEnrolment;

public class SubjectBuilder {
    private final Individual individual = new Individual();

    public Individual build() {
        return individual;
    }

    public SubjectBuilder addEnrolment(ProgramEnrolment programEnrolment) {
        individual.addEnrolment(programEnrolment);
        return this;
    }
}
