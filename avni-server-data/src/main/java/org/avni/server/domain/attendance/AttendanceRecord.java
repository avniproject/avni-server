package org.avni.server.domain.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.OrganisationAwareEntity;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "attendance_record")
@BatchSize(size = 100)
@JsonIgnoreProperties({"session", "subject", "reasonConcept"})
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reason_concept_id")
    private Concept reasonConcept;

    @Column(name = "follow_up_encounter_uuid")
    private String followUpEncounterUuid;

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

    public Concept getReasonConcept() {
        return reasonConcept;
    }

    public void setReasonConcept(Concept reasonConcept) {
        this.reasonConcept = reasonConcept;
    }

    public String getReasonConceptUUID() {
        return reasonConcept == null ? null : reasonConcept.getUuid();
    }

    public String getFollowUpEncounterUuid() {
        return followUpEncounterUuid;
    }

    public void setFollowUpEncounterUuid(String followUpEncounterUuid) {
        this.followUpEncounterUuid = followUpEncounterUuid;
    }
}
