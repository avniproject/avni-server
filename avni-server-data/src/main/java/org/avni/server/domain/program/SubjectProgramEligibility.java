package org.avni.server.domain.program;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.domain.Program;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import java.util.Date;

@Entity(name = "subject_program_eligibility")
public class SubjectProgramEligibility extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {
    @ManyToOne(targetEntity = Individual.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @NotNull
    private Individual subject;

    @ManyToOne(targetEntity = Program.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    @NotNull
    private Program program;

    @Column
    private boolean isEligible;

    @Column
    private DateTime checkDate;

    @Column(updatable = false)
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    @Column
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection observations;

    public Individual getSubject() {
        return subject;
    }

    public void setSubject(Individual subject) {
        this.subject = subject;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public boolean isEligible() {
        return isEligible;
    }

    public void setEligible(boolean eligible) {
        isEligible = eligible;
    }

    public DateTime getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(DateTime checkDate) {
        this.checkDate = checkDate;
    }

    public ObservationCollection getObservations() {
        return observations;
    }

    public void setObservations(ObservationCollection observations) {
        this.observations = observations;
    }

    public boolean isSyncDisabled() {
        return syncDisabled;
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getSubject());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getSubject());
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
        return null;
    }
}
