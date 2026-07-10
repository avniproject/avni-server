package org.avni.server.domain.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.Individual;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.framework.hibernate.StringListUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attendance_record")
@BatchSize(size = 100)
@JsonIgnoreProperties({"session", "subject"})
public class AttendanceRecord extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Individual subject;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column
    private AttendanceStatus status;

    @Column(name = "reason_concept_uuids")
    @Type(value = StringListUserType.class)
    private List<String> reasonConceptUuids = new ArrayList<>();

    @Column(name = "other_reason_text")
    private String otherReasonText;

    @Column(name = "follow_up_encounter_uuid")
    private String followUpEncounterUuid;

    @Column(name = "needs_follow_up", nullable = false)
    private boolean needsFollowUp = false;

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getSessionUUID() {
        return session == null ? null : session.getUuid();
    }

    public Individual getSubject() {
        return subject;
    }

    public void setSubject(Individual subject) {
        this.subject = subject;
    }

    public String getSubjectUUID() {
        return subject == null ? null : subject.getUuid();
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public List<String> getReasonConceptUUIDs() {
        return reasonConceptUuids;
    }

    public void setReasonConceptUUIDs(List<String> reasonConceptUuids) {
        this.reasonConceptUuids = reasonConceptUuids == null ? new ArrayList<>() : reasonConceptUuids;
    }

    public String getOtherReasonText() {
        return otherReasonText;
    }

    public void setOtherReasonText(String otherReasonText) {
        this.otherReasonText = otherReasonText;
    }

    public String getFollowUpEncounterUuid() {
        return followUpEncounterUuid;
    }

    public void setFollowUpEncounterUuid(String followUpEncounterUuid) {
        this.followUpEncounterUuid = followUpEncounterUuid;
    }

    public boolean isNeedsFollowUp() {
        return needsFollowUp;
    }

    public void setNeedsFollowUp(boolean needsFollowUp) {
        this.needsFollowUp = needsFollowUp;
    }
}
