package org.avni.server.web.request.attendance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceStatus;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AttendanceRecordContract extends CHSRequest {
    private String sessionUUID;
    private String subjectUUID;
    private AttendanceStatus status;
    private List<String> reasonConceptUUIDs = new ArrayList<>();
    // Backwards-compat: pre-16.15 clients POST a single reasonConceptUUID. Treated as a
    // one-element array by getReasonConceptUUIDs(). Write-only; never serialized back.
    private String reasonConceptUUID;
    private String followUpEncounterUUID;
    private boolean needsFollowUp;
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
        contract.setReasonConceptUUIDs(record.getReasonConceptUUIDs());
        contract.setFollowUpEncounterUUID(record.getFollowUpEncounterUuid());
        contract.setNeedsFollowUp(record.isNeedsFollowUp());
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

    public List<String> getReasonConceptUUIDs() {
        if (reasonConceptUUIDs != null && !reasonConceptUUIDs.isEmpty()) {
            return reasonConceptUUIDs;
        }
        if (reasonConceptUUID != null) {
            return Collections.singletonList(reasonConceptUUID);
        }
        return Collections.emptyList();
    }

    public void setReasonConceptUUIDs(List<String> reasonConceptUUIDs) {
        this.reasonConceptUUIDs = reasonConceptUUIDs == null ? new ArrayList<>() : reasonConceptUUIDs;
    }

    @JsonIgnore
    public String getReasonConceptUUID() {
        return reasonConceptUUID;
    }

    @JsonProperty("reasonConceptUUID")
    public void setReasonConceptUUID(String reasonConceptUUID) {
        this.reasonConceptUUID = reasonConceptUUID;
    }

    public String getFollowUpEncounterUUID() {
        return followUpEncounterUUID;
    }

    public void setFollowUpEncounterUUID(String followUpEncounterUUID) {
        this.followUpEncounterUUID = followUpEncounterUUID;
    }

    public boolean isNeedsFollowUp() {
        return needsFollowUp;
    }

    public void setNeedsFollowUp(boolean needsFollowUp) {
        this.needsFollowUp = needsFollowUp;
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
