package org.avni.server.service.attendance;

import java.util.List;

public class AttendanceConfigIncompleteException extends RuntimeException {
    private final List<IncompleteAttendanceType> incompleteTypes;

    public AttendanceConfigIncompleteException(List<IncompleteAttendanceType> incompleteTypes) {
        super("AttendanceConfigIncomplete");
        this.incompleteTypes = incompleteTypes;
    }

    public List<IncompleteAttendanceType> getIncompleteTypes() {
        return incompleteTypes;
    }
}
