package org.avni.server.domain.factory.metadata;

import org.avni.server.domain.DeclarativeRule;
import org.avni.server.domain.OperationalProgram;
import org.avni.server.domain.Program;

import java.util.Set;
import java.util.UUID;

public class ProgramBuilder {
    private final Program program = new Program();

    public ProgramBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public ProgramBuilder withName(String name) {
        program.setName(name);
        return this;
    }

    public ProgramBuilder withUuid(String uuid) {
        program.setUuid(uuid);
        return this;
    }

    public ProgramBuilder allowMultipleEnrolments(boolean allowMultipleEnrolments) {
        program.setAllowMultipleEnrolments(allowMultipleEnrolments);
        return this;
    }

    public ProgramBuilder setColour(String colour) {
        program.setColour(colour);
        return this;
    }

    public ProgramBuilder setOperationalPrograms(Set<OperationalProgram> operationalPrograms) {
        program.setOperationalPrograms(operationalPrograms);
        return this;
    }

    public ProgramBuilder setEnrolmentSummaryRule(String enrolmentSummaryRule) {
        program.setEnrolmentSummaryRule(enrolmentSummaryRule);
        return this;
    }

    public ProgramBuilder setEnrolmentEligibilityCheckRule(String enrolmentEligibilityCheckRule) {
        program.setEnrolmentEligibilityCheckRule(enrolmentEligibilityCheckRule);
        return this;
    }

    public ProgramBuilder setActive(Boolean active) {
        program.setActive(active);
        return this;
    }

    public ProgramBuilder setEnrolmentEligibilityCheckDeclarativeRule(DeclarativeRule enrolmentEligibilityCheckDeclarativeRule) {
        program.setEnrolmentEligibilityCheckDeclarativeRule(enrolmentEligibilityCheckDeclarativeRule);
        return this;
    }

    public ProgramBuilder setManualEnrolmentEligibilityCheckRule(String programEligibilityCheckRule) {
        program.setManualEnrolmentEligibilityCheckRule(programEligibilityCheckRule);
        return this;
    }

    public ProgramBuilder setManualEnrolmentEligibilityCheckDeclarativeRule(DeclarativeRule programEligibilityCheckDeclarativeRule) {
        program.setManualEnrolmentEligibilityCheckDeclarativeRule(programEligibilityCheckDeclarativeRule);
        return this;
    }

    public ProgramBuilder setManualEligibilityCheckRequired(boolean manualEligibilityCheckRequired) {
        program.setManualEligibilityCheckRequired(manualEligibilityCheckRequired);
        return this;
    }

    public Program build() {
        return program;
    }
}
