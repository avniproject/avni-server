package org.avni.server.domain.accessControl;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.PrivilegeEntityType;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

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
    private DateTime createdDateTime;

    @Column
    private DateTime lastModifiedDateTime;

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
