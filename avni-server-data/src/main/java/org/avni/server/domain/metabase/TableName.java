package org.avni.server.domain.metabase;

public enum TableName {
    PROGRAM_ENROLMENT("program_enrolment"),
    INDIVIDUAL("individual");

    private final String name;
    TableName(String name) {
        this.name =name;
    }

    public String getName() {
        return name;
    }
}
