package org.avni.server.service.attendance;

import java.util.List;

public class IncompleteAttendanceType {
    private final String uuid;
    private final String name;
    private final List<String> missingFields;

    public IncompleteAttendanceType(String uuid, String name, List<String> missingFields) {
        this.uuid = uuid;
        this.name = name;
        this.missingFields = missingFields;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
