package org.avni.server.domain.factory.txn;

import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSubjectAssignment;

import java.util.UUID;

public class TestUserSubjectAssignmentBuilder {
    private final UserSubjectAssignment entity = new UserSubjectAssignment();

    public TestUserSubjectAssignmentBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestUserSubjectAssignmentBuilder withUser(User user) {
        entity.setUser(user);
        return this;
    }

    public TestUserSubjectAssignmentBuilder withSubject(Individual subject) {
        entity.setSubject(subject);
        return this;
    }

    public TestUserSubjectAssignmentBuilder withMandatoryFieldsForNewEntity() {
        return withUuid(UUID.randomUUID().toString());
    }

<<<<<<< HEAD
=======
    public TestUserSubjectAssignmentBuilder setVoided(boolean voided) {
        entity.setVoided(voided);
        return this;
    }

>>>>>>> 4.0
    public UserSubjectAssignment build() {
        return entity;
    }
}
