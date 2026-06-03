package org.avni.server.web.request.attendance;

import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.attendance.SessionStatus;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class SessionContract extends CHSRequest {
    private String groupSubjectUUID;
    private LocalDate scheduledDate;
    private String attendanceTypeUUID;
    private SessionStatus status;
    private String reasonConceptUUID;
    private String notes;
    private DateTime markedAt;
    private List<AttendanceRecordContract> roster;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;

    public static SessionContract fromEntity(Session session, List<AttendanceRecord> records) {
        SessionContract contract = new SessionContract();
        contract.setId(session.getId());
        contract.setUuid(session.getUuid());
        contract.setVoided(session.isVoided());
        contract.setGroupSubjectUUID(session.getGroupSubjectUUID());
        contract.setScheduledDate(session.getScheduledDate());
        contract.setAttendanceTypeUUID(session.getAttendanceTypeUUID());
        contract.setStatus(session.getStatus());
        contract.setReasonConceptUUID(session.getReasonConceptUUID());
        contract.setNotes(session.getNotes());
        contract.setMarkedAt(session.getMarkedAt());
        contract.setCreatedDateTime(session.getCreatedDateTime());
        contract.setLastModifiedDateTime(session.getLastModifiedDateTime());
        if (records != null) {
            contract.setRoster(records.stream().map(AttendanceRecordContract::fromEntity).collect(Collectors.toList()));
        }
        return contract;
    }

    public String getGroupSubjectUUID() {
        return groupSubjectUUID;
    }

    public void setGroupSubjectUUID(String groupSubjectUUID) {
        this.groupSubjectUUID = groupSubjectUUID;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getAttendanceTypeUUID() {
        return attendanceTypeUUID;
    }

    public void setAttendanceTypeUUID(String attendanceTypeUUID) {
        this.attendanceTypeUUID = attendanceTypeUUID;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getReasonConceptUUID() {
        return reasonConceptUUID;
    }

    public void setReasonConceptUUID(String reasonConceptUUID) {
        this.reasonConceptUUID = reasonConceptUUID;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public DateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(DateTime markedAt) {
        this.markedAt = markedAt;
    }

    public List<AttendanceRecordContract> getRoster() {
        return roster;
    }

    public void setRoster(List<AttendanceRecordContract> roster) {
        this.roster = roster;
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
