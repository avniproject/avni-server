package org.avni.server.domain.factory.txn;

import org.avni.server.domain.*;
import org.avni.server.geo.Point;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.Set;
import java.util.UUID;

public class ProgramEnrolmentBuilder {
    private final ProgramEnrolment programEnrolment = new ProgramEnrolment();

    public ProgramEnrolment build() {
        return programEnrolment;
    }

    public ProgramEnrolmentBuilder setProgram(Program program) {
        programEnrolment.setProgram(program);
        return this;
    }

    public ProgramEnrolmentBuilder setUuid(String uuid) {
        programEnrolment.setUuid(uuid);
        return this;
    }

    public ProgramEnrolmentBuilder withMandatoryFieldsForNewEntity() {
        String s = UUID.randomUUID().toString();
        return setUuid(s).setEnrolmentDateTime(DateTime.now());
    }

    public ProgramEnrolmentBuilder setIndividual(Individual individual) {
        programEnrolment.setIndividual(individual);
        individual.addEnrolment(programEnrolment);
        return this;
    }

    public ProgramEnrolmentBuilder setProgramEncounters(Set<ProgramEncounter> programEncounters) {
        programEnrolment.setProgramEncounters(programEncounters);
        return this;
    }

    public ProgramEnrolmentBuilder setEnrolmentDateTime(DateTime enrolmentDateTime) {
        programEnrolment.setEnrolmentDateTime(enrolmentDateTime);
        return this;
    }

    public ProgramEnrolmentBuilder setObservations(ObservationCollection observations) {
        programEnrolment.setObservations(observations);
        return this;
    }

    public ProgramEnrolmentBuilder setProgramExitDateTime(DateTime programExitDateTime) {
        programEnrolment.setProgramExitDateTime(programExitDateTime);
        return this;
    }

    public ProgramEnrolmentBuilder setProgramExitObservations(ObservationCollection programExitObservations) {
        programEnrolment.setProgramExitObservations(programExitObservations);
        return this;
    }

    public ProgramEnrolmentBuilder setEnrolmentLocation(Point enrolmentLocation) {
        programEnrolment.setEnrolmentLocation(enrolmentLocation);
        return this;
    }

    public ProgramEnrolmentBuilder setExitLocation(Point exitLocation) {
        programEnrolment.setExitLocation(exitLocation);
        return this;
    }

    public ProgramEnrolmentBuilder setLegacyId(String legacyId) {
        programEnrolment.setLegacyId(legacyId);
        return this;
    }

    public ProgramEnrolmentBuilder setAddressId(Long addressId) {
        programEnrolment.setAddressId(addressId);
        return this;
    }

    public void setSyncConcept1Value(String syncConcept1Value) {
        programEnrolment.setSyncConcept1Value(syncConcept1Value);
    }

    public void setSyncConcept2Value(String syncConcept2Value) {
        programEnrolment.setSyncConcept2Value(syncConcept2Value);
    }
}
