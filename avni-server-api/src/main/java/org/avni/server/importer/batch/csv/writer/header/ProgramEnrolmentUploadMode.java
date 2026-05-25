package org.avni.server.importer.batch.csv.writer.header;

public enum ProgramEnrolmentUploadMode {
    UPLOAD_ENROLMENT("upload_enrolments"),
    UPLOAD_EXITED_ENROLMENT("upload_exited_enrolments");

    private final String value;

    ProgramEnrolmentUploadMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProgramEnrolmentUploadMode fromString(String value) {
        for (ProgramEnrolmentUploadMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
