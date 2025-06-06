package org.avni.server.domain.factory.txn;

import org.avni.server.domain.*;
import org.joda.time.LocalDate;

import java.util.Date;
import java.util.UUID;

public class SubjectBuilder {
    private final Individual individual = new Individual();

    public SubjectBuilder() {
        this.withUUID(UUID.randomUUID().toString());
    }

    public Individual build() {
        return individual;
    }

    public SubjectBuilder withUUID(String uuid) {
        individual.setUuid(uuid);
        return this;
    }

    public SubjectBuilder setId(int id) {
        individual.setId((long) id);
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

    public SubjectBuilder withObservations(ObservationCollection observations) {
        individual.setObservations(observations);
        return this;
    }

    public SubjectBuilder withFirstName(String firstName) {
        individual.setFirstName(firstName);
    	return this;
    }

    public SubjectBuilder withLocation(AddressLevel addressLevel) {
        individual.setAddressLevel(addressLevel);
    	return this;
    }

    public SubjectBuilder withRegistrationDate(LocalDate date) {
        individual.setRegistrationDate(date);
    	return this;
    }

    public SubjectBuilder withMandatoryFieldsForNewEntity() {
        String s = UUID.randomUUID().toString();
        return withUUID(s).withFirstName(s).withRegistrationDate(LocalDate.now()).withObservations(new ObservationCollection());
    }

    public SubjectBuilder withSyncConcept1Value(Object value) {
        individual.setSyncConcept1Value(value.toString());
        return this;
    }

    public SubjectBuilder withLegacyId(String legacyId) {
        individual.setLegacyId(legacyId);
        return this;
    }

    public SubjectBuilder withSyncDisabled(boolean syncDisabled) {
        individual.setSyncDisabled(syncDisabled);
        return this;
    }
}
