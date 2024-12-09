package org.avni.server.domain.task;

import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.domain.User;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "task")
@BatchSize(size = 100)
public class Task extends OrganisationAwareEntity {
    @Column
    @NotNull
    private String name;

    @ManyToOne(targetEntity = TaskType.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_type_id")
    @NotNull
    private TaskType taskType;

    @ManyToOne(targetEntity = TaskStatus.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_status_id")
    @NotNull
    private TaskStatus taskStatus;

    @Column
    private Instant scheduledOn;

    @Column
    private Instant completedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedTo;

    @Column
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection metadata;

    @ManyToOne(targetEntity = Individual.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Individual subject;

    @Column
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection observations;

    @Column
    private String legacyId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(TaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }
    public void updateTaskStatus(TaskStatus taskStatus) {
        setTaskStatus(taskStatus);
        if (taskStatus.isTerminal()) {
            setCompletedOn(new DateTime());
        }
    }

    public DateTime getScheduledOn() {
        return DateTimeUtil.toJodaDateTime(scheduledOn);
    }

    public void setScheduledOn(DateTime scheduledOn) {
        this.scheduledOn = DateTimeUtil.toInstant(scheduledOn);
    }

    public DateTime getCompletedOn() {
        return DateTimeUtil.toJodaDateTime(completedOn);
    }

    public void setCompletedOn(DateTime completedOn) {
        this.completedOn = DateTimeUtil.toInstant(completedOn);
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public ObservationCollection getMetadata() {
        return metadata;
    }

    public void setMetadata(ObservationCollection metadata) {
        this.metadata = metadata;
    }

    public Individual getSubject() {
        return subject;
    }

    public void setSubject(Individual subject) {
        this.subject = subject;
    }

    public ObservationCollection getObservations() {
        return observations;
    }

    public void setObservations(ObservationCollection observations) {
        this.observations = observations;
    }

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }
}
