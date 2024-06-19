package org.avni.server.projection;

import org.avni.server.domain.Program;
import org.avni.server.web.request.EnrolmentContract;

public class SearchSubjectEnrolledProgram {
    private Long id;
    private Program program;

    public SearchSubjectEnrolledProgram(Long id, Program program) {
        this.id = id;
        this.program = program;
    }

    public Long getId() {
        return id;
    }

    public EnrolmentContract getProgram() {
        EnrolmentContract enrolmentContract = new EnrolmentContract();
        enrolmentContract.setOperationalProgramName(program.getOperationalProgramName());
        enrolmentContract.setProgramColor(program.getColour());
        return enrolmentContract;
    }
}
