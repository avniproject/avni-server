package org.avni.server.web.request.attendance;

import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

import java.util.Map;

public class AttendanceTypeContract extends CHSRequest {
    private String subjectTypeUUID;
    private String name;
    private Integer sortOrder;
    private Map<String, Object> config;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;

    public static AttendanceTypeContract fromEntity(AttendanceType attendanceType) {
        AttendanceTypeContract contract = new AttendanceTypeContract();
        contract.setId(attendanceType.getId());
        contract.setUuid(attendanceType.getUuid());
        contract.setVoided(attendanceType.isVoided());
        contract.setSubjectTypeUUID(attendanceType.getSubjectTypeUUID());
        contract.setName(attendanceType.getName());
        contract.setSortOrder(attendanceType.getSortOrder());
        contract.setConfig(attendanceType.getConfig());
        contract.setCreatedDateTime(attendanceType.getCreatedDateTime());
        contract.setLastModifiedDateTime(attendanceType.getLastModifiedDateTime());
        return contract;
    }

    public String getSubjectTypeUUID() {
        return subjectTypeUUID;
    }

    public void setSubjectTypeUUID(String subjectTypeUUID) {
        this.subjectTypeUUID = subjectTypeUUID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }
}
