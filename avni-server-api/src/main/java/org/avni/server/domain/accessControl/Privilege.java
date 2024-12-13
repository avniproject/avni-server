package org.avni.server.domain.accessControl;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.PrivilegeEntityType;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import java.time.Instant;

@Entity
@Table(name = "privilege")
@BatchSize(size = 100)
public class Privilege extends CHSBaseEntity {
    @Column
    @NotNull
    private String name;

    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private PrivilegeType type;

    @Column
    private String description;

    @Column
    @Enumerated(EnumType.STRING)
    private PrivilegeEntityType entityType;

    @Column
    private Instant createdDateTime;

    @Column
    private Instant lastModifiedDateTime;

    public DateTime getCreatedDateTime() {
        return DateTimeUtil.toJodaDateTime(createdDateTime);
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = DateTimeUtil.toInstant(createdDateTime);
    }

    public DateTime getLastModifiedDateTime() {
        return DateTimeUtil.toJodaDateTime(lastModifiedDateTime);
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = DateTimeUtil.toInstant(lastModifiedDateTime);
    }

    public String getName() {
        return name;
    }

    /**
     * Use only for sync
     */
    @Deprecated
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PrivilegeEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(PrivilegeEntityType entityType) {
        this.entityType = entityType;
    }

    public PrivilegeType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Privilege{" +
                "type=" + type +
                '}';
    }
}
