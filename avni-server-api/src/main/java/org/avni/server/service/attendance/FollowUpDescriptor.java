package org.avni.server.service.attendance;

import org.joda.time.DateTime;

public class FollowUpDescriptor {
    private final String studentUUID;
    private final String studentName;
    private final String encounterUUID;
    private final String encounterTypeName;
    private final DateTime earliestVisitDateTime;
    private final DateTime maxVisitDateTime;

    public FollowUpDescriptor(String studentUUID, String studentName, String encounterUUID,
                              String encounterTypeName, DateTime earliestVisitDateTime, DateTime maxVisitDateTime) {
        this.studentUUID = studentUUID;
        this.studentName = studentName;
        this.encounterUUID = encounterUUID;
        this.encounterTypeName = encounterTypeName;
        this.earliestVisitDateTime = earliestVisitDateTime;
        this.maxVisitDateTime = maxVisitDateTime;
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

    public String getEncounterTypeName() {
        return encounterTypeName;
    }

    public DateTime getEarliestVisitDateTime() {
        return earliestVisitDateTime;
    }

    public DateTime getMaxVisitDateTime() {
        return maxVisitDateTime;
    }
}
