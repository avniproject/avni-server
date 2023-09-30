package org.avni.server.domain;

import org.joda.time.DateTime;

public class TestSubjectMigrationBuilder {
    private final SubjectMigration subjectMigration = new SubjectMigration();

    public TestSubjectMigrationBuilder setId(Long id) {
        subjectMigration.setId(id);
        return this;
    }

    public TestSubjectMigrationBuilder setVoided(boolean voided) {
        subjectMigration.setVoided(voided);
        return this;
    }

    public TestSubjectMigrationBuilder setUuid(String uuid) {
        subjectMigration.setUuid(uuid);
        return this;
    }

    public TestSubjectMigrationBuilder setCreatedBy(User createdBy) {
        subjectMigration.setCreatedBy(createdBy);
        return this;
    }

    public TestSubjectMigrationBuilder setCreatedDateTime(DateTime createdDateTime) {
        subjectMigration.setCreatedDateTime(createdDateTime);
        return this;
    }

    public TestSubjectMigrationBuilder setLastModifiedBy(User lastModifiedBy) {
        subjectMigration.setLastModifiedBy(lastModifiedBy);
        return this;
    }

    public TestSubjectMigrationBuilder setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        subjectMigration.setLastModifiedDateTime(lastModifiedDateTime);
        return this;
    }

    public TestSubjectMigrationBuilder setVersion(int version) {
        subjectMigration.setVersion(version);
        return this;
    }

    public TestSubjectMigrationBuilder setOrganisationId(Long organisationId) {
        subjectMigration.setOrganisationId(organisationId);
        return this;
    }

    public TestSubjectMigrationBuilder setOldAddressLevel(AddressLevel oldAddressLevel) {
        subjectMigration.setOldAddressLevel(oldAddressLevel);
        return this;
    }

    public TestSubjectMigrationBuilder setNewAddressLevel(AddressLevel newAddressLevel) {
        subjectMigration.setNewAddressLevel(newAddressLevel);
        return this;
    }

    public TestSubjectMigrationBuilder setIndividual(Individual individual) {
        subjectMigration.setIndividual(individual);
        return this;
    }

    public TestSubjectMigrationBuilder setOldSyncConcept1Value(String oldSyncConcept1Value) {
        subjectMigration.setOldSyncConcept1Value(oldSyncConcept1Value);
        return this;
    }

    public TestSubjectMigrationBuilder setNewSyncConcept1Value(String newSyncConcept1Value) {
        subjectMigration.setNewSyncConcept1Value(newSyncConcept1Value);
        return this;
    }

    public TestSubjectMigrationBuilder setOldSyncConcept2Value(String oldSyncConcept2Value) {
        subjectMigration.setOldSyncConcept2Value(oldSyncConcept2Value);
        return this;
    }

    public TestSubjectMigrationBuilder setNewSyncConcept2Value(String newSyncConcept2Value) {
        subjectMigration.setNewSyncConcept2Value(newSyncConcept2Value);
        return this;
    }

    public TestSubjectMigrationBuilder setSubjectType(SubjectType subjectType) {
        subjectMigration.setSubjectType(subjectType);
        return this;
    }

    public SubjectMigration build() {
        return subjectMigration;
    }
}
