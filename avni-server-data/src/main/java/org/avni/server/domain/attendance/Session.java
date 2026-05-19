package org.avni.server.domain.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.domain.User;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import java.time.LocalDate;

@Entity
@Table(name = "session")
@BatchSize(size = 100)
@JsonIgnoreProperties({"groupSubject", "attendanceType", "reasonConcept", "markedByUser"})
public class Session extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_subject_id")
    private Individual groupSubject;

    @NotNull
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_type_id")
    private AttendanceType attendanceType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column
    private SessionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reason_concept_id")
    private Concept reasonConcept;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_user_id")
    private User markedByUser;

    @Column(name = "marked_at")
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime markedAt;

    public Individual getGroupSubject() {
        return groupSubject;
    }

    public void setGroupSubject(Individual groupSubject) {
        this.groupSubject = groupSubject;
    }

    public String getGroupSubjectUUID() {
        return groupSubject == null ? null : groupSubject.getUuid();
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public AttendanceType getAttendanceType() {
        return attendanceType;
    }

    public void setAttendanceType(AttendanceType attendanceType) {
        this.attendanceType = attendanceType;
    }

    public String getAttendanceTypeUUID() {
        return attendanceType == null ? null : attendanceType.getUuid();
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getMarkedByUser() {
        return markedByUser;
    }

    public void setMarkedByUser(User markedByUser) {
        this.markedByUser = markedByUser;
    }

    public DateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(DateTime markedAt) {
        this.markedAt = markedAt;
    }
}
