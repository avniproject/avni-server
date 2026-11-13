package org.avni.server.projection;

import org.avni.server.domain.Program;
import org.joda.time.DateTime;

public class SearchSubjectEnrolledProgram {
    private Long id;
    private Program program;
    private DateTime programExitDateTime;

    public SearchSubjectEnrolledProgram(Long id, Program program, DateTime programExitDateTime) {
        this.id = id;
        this.program = program;
        this.programExitDateTime = programExitDateTime;
    }

    public Long getId() {
        return id;
    }

    public Program getProgram() {
        return program;
    }

    public DateTime getProgramExitDateTime() {
        return programExitDateTime;
    }
}
