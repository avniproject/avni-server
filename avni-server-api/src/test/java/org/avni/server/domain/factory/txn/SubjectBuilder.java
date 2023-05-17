package org.avni.server.domain.factory.txn;

import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;

public class SubjectBuilder {
    private final Individual individual = new Individual();

    public Individual build() {
        return individual;
    }

    public SubjectBuilder withUUID(String uuid) {
        individual.setUuid(uuid);
    	return this;
    }

    public SubjectBuilder addEnrolment(ProgramEnrolment programEnrolment) {
        individual.addEnrolment(programEnrolment);
        return this;
    }

    public SubjectBuilder withSubjectType(SubjectType subjectType) {
        individual.setSubjectType(subjectType);
    	return this;
    }

    public SubjectBuilder withAuditUser(User user) {
        individual.setCreatedBy(user);
        individual.setLastModifiedBy(user);
    	return this;
    }
}
