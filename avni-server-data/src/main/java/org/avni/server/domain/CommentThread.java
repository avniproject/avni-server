package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "comment_thread")
@BatchSize(size = 100)
@JsonIgnoreProperties({"comments"})
public class CommentThread extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {
    @NotNull
    @Enumerated(EnumType.STRING)
    private CommentThreadStatus status;

    @Column
    @NotNull
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime openDateTime;

    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime resolvedDateTime;

    @Column(updatable = false)
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "commentThread")
    private Set<Comment> comments = new HashSet<>();

    public CommentThreadStatus getStatus() {
        return status;
    }

    public void setStatus(CommentThreadStatus status) {
        this.status = status;
    }

    public DateTime getOpenDateTime() {
        return openDateTime;
    }

    public void setOpenDateTime(DateTime openDateTime) {
        this.openDateTime = openDateTime;
    }

    public DateTime getResolvedDateTime() {
        return resolvedDateTime;
    }

    public void setResolvedDateTime(DateTime resolvedDateTime) {
        this.resolvedDateTime = resolvedDateTime;
    }

    public Set<Comment> getComments() {
        return comments;
    }

    public void setComments(Set<Comment> comments) {
        this.comments = comments;
    }

    @JsonIgnore
    public Set<Comment> getNonVoidedComments() {
        return comments.stream().filter(c -> !c.isVoided()).collect(Collectors.toSet());
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

    public enum CommentThreadStatus {
        Open,
        Resolved
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this,
                this.getComments().stream()
                        .map(Comment::getSubject)
                        .collect(Collectors.toList()));
    }

    @PreUpdate
    public void beforeUpdate() {
        this.beforeSave();
    }
}
