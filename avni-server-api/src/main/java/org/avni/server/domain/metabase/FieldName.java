package org.avni.server.domain.metabase;

public enum FieldName {
    REGISTRATION_DATE("registration_date"),
    ENROLMENT_DATE_TIME("enrolment_date_time");

    private final String name;
    FieldName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
