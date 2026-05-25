package org.avni.server.service.attendance;

public class SkippedFollowUp {
    private final String studentUUID;
    private final String studentName;
    private final String encounterUUID;
    private final String reason;

    public SkippedFollowUp(String studentUUID, String studentName, String encounterUUID, String reason) {
        this.studentUUID = studentUUID;
        this.studentName = studentName;
        this.encounterUUID = encounterUUID;
        this.reason = reason;
    }

    public String getStudentUUID() {
        return studentUUID;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getEncounterUUID() {
        return encounterUUID;
    }

    public String getReason() {
        return reason;
    }
}
