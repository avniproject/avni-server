package org.avni.server.domain.accessControl;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.avni.server.domain.PrivilegeEntityType;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

@Entity
@Table(name = "privilege")
@BatchSize(size = 100)
public class Privilege {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;

    @Column
    @NotNull
    private String uuid;

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
    private boolean isVoided;

    @Column
    private DateTime createdDateTime;

    @Column
    private DateTime lastModifiedDateTime;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
