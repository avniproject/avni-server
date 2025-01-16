package org.avni.server.projection;

import org.avni.server.domain.Program;

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

    public Program getProgram() {
        return program;
    }
}
