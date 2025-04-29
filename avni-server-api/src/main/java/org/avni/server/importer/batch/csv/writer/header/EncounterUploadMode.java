package org.avni.server.importer.batch.csv.writer.header;

public enum EncounterUploadMode {
    SCHEDULE_VISIT("schedule_a_visit"),
    UPLOAD_VISIT_DETAILS("upload_visit_details");

    private final String value;

    EncounterUploadMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EncounterUploadMode fromString(String value) {
        for (EncounterUploadMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    public static boolean isScheduleVisitMode(String mode) {
        return mode != null && EncounterUploadMode.fromString(mode).equals(SCHEDULE_VISIT);
    }
}
