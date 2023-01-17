package org.avni.server.domain.factory.metadata;

import org.avni.server.domain.Program;

public class ProgramBuilder {
    private final Program program = new Program();

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

    public Program build() {
        return program;
    }
}
