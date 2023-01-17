package org.avni.server.domain.factory.txn;

import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;

public class ProgramEnrolmentBuilder {
    private final ProgramEnrolment programEnrolment = new ProgramEnrolment();

    public ProgramEnrolment build() {
        return programEnrolment;
    }

    public ProgramEnrolmentBuilder program(Program program) {
        programEnrolment.setProgram(program);
        return this;
    }
}
