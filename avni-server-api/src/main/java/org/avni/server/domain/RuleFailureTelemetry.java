package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.enums.ruleFailure.AppType;
import org.avni.server.domain.enums.ruleFailure.EntityType;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "rule_failure_telemetry")
@BatchSize(size = 100)
@EntityListeners({AuditingEntityListener.class})
public class RuleFailureTelemetry {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private String ruleUuid;

    @Column
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column
    private String sourceId;

    @Column
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column
    private String entityId;

    @Column
    @Enumerated(EnumType.STRING)
    private AppType appType;

    @Column
    private String individualUuid;

    @Column
    private String errorMessage;

    @Column
    private String stacktrace;

    @Column
    private Instant closedDateTime;

    @Column
    private Instant errorDateTime;

    @Column
    private Boolean isClosed;

    @Column
    private Long organisationId;

    @JsonIgnore
    @JoinColumn(name = "created_by_id")
    @CreatedBy
    @ManyToOne(targetEntity = User.class)
    @Fetch(FetchMode.JOIN)
    @NotNull
    private User createdBy;

    @CreatedDate
    private Date createdDateTime;

    @JsonIgnore
    @JoinColumn(name = "last_modified_by_id")
    @LastModifiedBy
    @ManyToOne(targetEntity = User.class)
    @Fetch(FetchMode.JOIN)
    @NotNull
    private User lastModifiedBy;

    @LastModifiedDate
    private Date lastModifiedDateTime;

    @Column(name = "version")
    private int version;

    public User getCreatedBy() {
        return createdBy;
    }

    public DateTime getCreatedDateTime() {
        return new DateTime(createdDateTime);
    }

    public User getLastModifiedBy() {
        return lastModifiedBy;
    }

    public DateTime getLastModifiedDateTime() {
        return new DateTime(lastModifiedDateTime);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRuleUuid() {
        return ruleUuid;
    }

    public void setRuleUuid(String ruleUuid) {
        this.ruleUuid = ruleUuid;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public AppType getAppType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType;
    }

    public String getIndividualUuid() {
        return individualUuid;
    }

    public void setIndividualUuid(String individualUuid) {
        this.individualUuid = individualUuid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public void setStacktrace(String stacktrace) {
        this.stacktrace = stacktrace;
    }

    public DateTime getClosedDateTime() {
        return DateTimeUtil.toJodaDateTime(closedDateTime);
    }

    public void setClosedDateTime(DateTime closedDateTime) {
        this.closedDateTime = DateTimeUtil.toInstant(closedDateTime);
    }

    public DateTime getErrorDateTime() {
        return DateTimeUtil.toJodaDateTime(errorDateTime);
    }

    public void setErrorDateTime(DateTime errorDateTime) {
        this.errorDateTime = DateTimeUtil.toInstant(errorDateTime);
    }

    public Boolean getClosed() {
        return isClosed;
    }

    public void setClosed(Boolean closed) {
        isClosed = closed;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }
}
