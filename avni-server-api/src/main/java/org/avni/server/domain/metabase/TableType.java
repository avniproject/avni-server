package org.avni.server.domain.metabase;

public enum TableType {
    INDIVIDUAL("Individual"),
    HOUSEHOLD("Household"),
    GROUP("Group"),
    PERSON("Person"),
    ENCOUNTER("Encounter"),
    PROGRAM_ENCOUNTER("ProgramEncounter"),
    PROGRAM_ENROLMENT("ProgramEnrolment");

    private final String typeName;

    TableType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public static TableType fromString(String typeName) {
        for (TableType type : TableType.values()) {
            if (type.getTypeName().equalsIgnoreCase(typeName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown table type: " + typeName);
    }
}
