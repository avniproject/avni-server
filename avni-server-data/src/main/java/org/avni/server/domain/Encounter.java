package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.application.projections.BaseProjection;
import org.avni.server.common.dbSchema.TableNames;
import org.avni.server.domain.EncounterType.EncounterTypeProjection;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;
import org.springframework.data.rest.core.config.Projection;

import java.util.Date;

@Entity
@Table(name = TableNames.Encounter)
@JsonIgnoreProperties({"individual"})
@BatchSize(size = 100)
public class Encounter extends AbstractEncounter implements MessageableEntity, SubjectLinkedSyncEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id")
    private Individual individual;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public Individual getIndividual() {
        return individual;
    }

    public void setIndividual(Individual individual) {
        this.individual = individual;
    }

    @Override
    @JsonIgnore
    public Long getEntityTypeId() {
        return getEncounterType().getId();
    }

    @Override
    @JsonIgnore
    public Long getEntityId() {
        return getId();
    }

    @Projection(name = "EncounterProjectionMinimal", types = {Encounter.class})
    public interface EncounterProjectionMinimal extends BaseProjection {
        EncounterTypeProjection getEncounterType();

        DateTime getEncounterDateTime();
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getIndividual());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getIndividual());
    }

    @Override
    public void setSyncDisabledDateTime(Date syncDisabledDateTime) {
        this.syncDisabledDateTime = syncDisabledDateTime;
    }
}
