package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "comment_thread")
@BatchSize(size = 100)
@JsonIgnoreProperties({"comments"})
public class CommentThread extends OrganisationAwareEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    private CommentThreadStatus status;

    @Column
    @NotNull
    private Instant openDateTime;

    @Column
    private Instant resolvedDateTime;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "commentThread")
    private Set<Comment> comments = new HashSet<>();

    public CommentThreadStatus getStatus() {
        return status;
    }

    public void setStatus(CommentThreadStatus status) {
        this.status = status;
    }

    public DateTime getOpenDateTime() {
        return DateTimeUtil.toJodaDateTime(openDateTime);
    }

    public void setOpenDateTime(DateTime openDateTime) {
        this.openDateTime = DateTimeUtil.toInstant(openDateTime);
    }

    public DateTime getResolvedDateTime() {
        return DateTimeUtil.toJodaDateTime(resolvedDateTime);
    }

    public void setResolvedDateTime(DateTime resolvedDateTime) {
        this.resolvedDateTime = DateTimeUtil.toInstant(resolvedDateTime);
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

    public enum CommentThreadStatus {
        Open,
        Resolved
    }
}
