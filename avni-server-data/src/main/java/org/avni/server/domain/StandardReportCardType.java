package org.avni.server.domain;

import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@BatchSize(size = 100)
public class StandardReportCardType {

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
    private String description;
    @Column
    private boolean isVoided;
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime createdDateTime;
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime lastModifiedDateTime;
    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private StandardReportCardTypeType type;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public @NotNull StandardReportCardTypeType getType() {
        return type;
    }

    public void setType(@NotNull StandardReportCardTypeType type) {
        this.type = type;
    }
}
