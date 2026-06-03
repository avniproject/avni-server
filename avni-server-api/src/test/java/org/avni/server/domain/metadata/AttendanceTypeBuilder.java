package org.avni.server.domain.metadata;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.attendance.AttendanceType;

import java.util.UUID;

public class AttendanceTypeBuilder {
    private final AttendanceType attendanceType;

    public AttendanceTypeBuilder() {
        attendanceType = new AttendanceType();
        this.setUuid(UUID.randomUUID().toString());
    }

    public AttendanceTypeBuilder(AttendanceType attendanceType) {
        this.attendanceType = attendanceType;
    }

    public AttendanceTypeBuilder setUuid(String uuid) {
        attendanceType.setUuid(uuid);
        return this;
    }

    public AttendanceTypeBuilder setId(Long id) {
        attendanceType.setId(id);
        return this;
    }

    public AttendanceTypeBuilder setName(String name) {
        attendanceType.setName(name);
        return this;
    }

    public AttendanceTypeBuilder setSubjectType(SubjectType subjectType) {
        attendanceType.setSubjectType(subjectType);
        return this;
    }

    public AttendanceTypeBuilder setSortOrder(Integer sortOrder) {
        attendanceType.setSortOrder(sortOrder);
        return this;
    }

    public AttendanceTypeBuilder setConfig(JsonObject config) {
        attendanceType.setConfig(config);
        return this;
    }

    public AttendanceTypeBuilder setVoided(boolean voided) {
        attendanceType.setVoided(voided);
        return this;
    }

    public AttendanceType build() {
        return attendanceType;
    }
}
