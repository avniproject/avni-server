package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.hibernate.annotations.BatchSize;

import java.util.Date;

@Entity
@Table(name = "user_subject_assignment")
@BatchSize(size = 100)
public class UserSubjectAssignment extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Individual subject;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Individual getSubject() {
        return subject;
    }

    public String getSubjectIdAsString() {
        return String.valueOf(subject.getId());
    }

    public void setSubject(Individual subject) {
        this.subject = subject;
    }

    public static UserSubjectAssignment createNew(User user, Individual subject) {
        UserSubjectAssignment userSubjectAssignment = new UserSubjectAssignment();
        userSubjectAssignment.assignUUID();
        userSubjectAssignment.setUser(user);
        userSubjectAssignment.setSubject(subject);
        return userSubjectAssignment;
    }

    public boolean isSyncDisabled() {
        return syncDisabled;
    }

    @Override
    public void setSyncDisabledDateTime(Date syncDisabledDateTime) {
        this.syncDisabledDateTime = syncDisabledDateTime;
    }

    public void setSyncDisabled(boolean syncDisabled) {
        this.syncDisabled = syncDisabled;
    }

    @Override
    public Date getSyncDisabledDateTime() {
        return this.syncDisabledDateTime;
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getSubject());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getSubject());
    }
}
