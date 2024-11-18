package org.avni.server.domain;


import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.joda.time.DateTime;

@Entity
@BatchSize(size = 100)
public class ApprovalStatus {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;
    @Column
    @NotNull
    private String uuid;
    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column
    private boolean isVoided;
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime createdDateTime;
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime lastModifiedDateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isVoided() {
        return isVoided;
    }

    public void setVoided(boolean voided) {
        isVoided = voided;
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

    public enum Status {
        Pending,
        Approved,
        Rejected
    }
}
