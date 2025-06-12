package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.common.dbSchema.TableNames;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;
import org.avni.server.application.projections.BaseProjection;
import org.avni.server.domain.EncounterType.EncounterTypeProjection;
import org.springframework.data.rest.core.config.Projection;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

@Entity
@Table(name = TableNames.ProgramEncounter)
@JsonIgnoreProperties({"programEnrolment", "individual"})
@BatchSize(size = 100)
public class ProgramEncounter extends AbstractEncounter implements MessageableEntity, SubjectLinkedSyncEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrolment_id")
    private ProgramEnrolment programEnrolment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id")
    private Individual individual;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public ProgramEnrolment getProgramEnrolment() {
        return programEnrolment;
    }

    public void setProgramEnrolment(ProgramEnrolment programEnrolment) {
        this.programEnrolment = programEnrolment;
    }

    @JsonIgnore
    public Individual getIndividual() {
        return individual;
    }

    public void setIndividual(Individual individual) {
        this.individual = individual;
    }

    @Override
    @JsonIgnore
    public Long getEntityTypeId() {
        return this.getEncounterType().getId();
    }

    @Override
    @JsonIgnore
    public Long getEntityId() {
        return getId();
    }

    @Override
    public void setSyncDisabledDateTime(Date syncDisabledDateTime) {
        this.syncDisabledDateTime = syncDisabledDateTime;
    }

    @Override
    public void setSyncDisabled(boolean syncDisabled) {
        this.syncDisabled = syncDisabled;
    }

    @Override
    public Date getSyncDisabledDateTime() {
        return this.syncDisabledDateTime;
    }

    @Projection(name = "ProgramEncounterProjectionMinimal", types = {ProgramEncounter.class})
    public interface ProgramEncounterProjectionMinimal extends BaseProjection {
        EncounterTypeProjection getEncounterType();

        String getName();

        DateTime getEncounterDateTime();

        DateTime getEarliestVisitDateTime();

        DateTime getMaxVisitDateTime();

        DateTime getCancelDateTime();
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getProgramEnrolment().getIndividual());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getProgramEnrolment().getIndividual());
    }
}
