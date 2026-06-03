package org.avni.server.web.request.attendance;

import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceStatus;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

public class AttendanceRecordContract extends CHSRequest {
    private String sessionUUID;
    private String subjectUUID;
    private AttendanceStatus status;
    private String reasonConceptUUID;
    private String followUpEncounterUUID;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;

    public static AttendanceRecordContract fromEntity(AttendanceRecord record) {
        AttendanceRecordContract contract = new AttendanceRecordContract();
        contract.setId(record.getId());
        contract.setUuid(record.getUuid());
        contract.setVoided(record.isVoided());
        contract.setSessionUUID(record.getSessionUUID());
        contract.setSubjectUUID(record.getSubjectUUID());
        contract.setStatus(record.getStatus());
        contract.setReasonConceptUUID(record.getReasonConceptUUID());
        contract.setFollowUpEncounterUUID(record.getFollowUpEncounterUuid());
        contract.setCreatedDateTime(record.getCreatedDateTime());
        contract.setLastModifiedDateTime(record.getLastModifiedDateTime());
        return contract;
    }

    public String getSessionUUID() {
        return sessionUUID;
    }

    public void setSessionUUID(String sessionUUID) {
        this.sessionUUID = sessionUUID;
    }

    public String getSubjectUUID() {
        return subjectUUID;
    }

    public void setSubjectUUID(String subjectUUID) {
        this.subjectUUID = subjectUUID;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getReasonConceptUUID() {
        return reasonConceptUUID;
    }

    public void setReasonConceptUUID(String reasonConceptUUID) {
        this.reasonConceptUUID = reasonConceptUUID;
    }

    public String getFollowUpEncounterUUID() {
        return followUpEncounterUUID;
    }

    public void setFollowUpEncounterUUID(String followUpEncounterUUID) {
        this.followUpEncounterUUID = followUpEncounterUUID;
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
