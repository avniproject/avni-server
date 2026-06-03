package org.avni.server.service.attendance;

public class DanglingRefWarning {
    private final String attendanceTypeUUID;
    private final String attendanceTypeName;
    private final String configKey;
    private final String referencedUUID;
    private final String reason;

    public DanglingRefWarning(String attendanceTypeUUID, String attendanceTypeName, String configKey, String referencedUUID, String reason) {
        this.attendanceTypeUUID = attendanceTypeUUID;
        this.attendanceTypeName = attendanceTypeName;
        this.configKey = configKey;
        this.referencedUUID = referencedUUID;
        this.reason = reason;
    }

    public String getAttendanceTypeUUID() {
        return attendanceTypeUUID;
    }

    public String getAttendanceTypeName() {
        return attendanceTypeName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getReferencedUUID() {
        return referencedUUID;
    }

    public String getReason() {
        return reason;
    }
}
