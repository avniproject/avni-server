package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import java.util.Date;

@Entity
@Table(name = "checklist_item")
@JsonIgnoreProperties({"checklist"})
@BatchSize(size = 100)
public class ChecklistItem extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime completionDate;

    @Column
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection observations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_item_detail_id")
    private ChecklistItemDetail checklistItemDetail;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id")
    private Checklist checklist;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public DateTime getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(DateTime completionDate) {
        this.completionDate = completionDate;
    }

    public Checklist getChecklist() {
        return checklist;
    }

    public void setChecklist(Checklist checklist) {
        this.checklist = checklist;
    }


    public ObservationCollection getObservations() {
        return observations;
    }

    public void setObservations(ObservationCollection observations) {
        this.observations = observations;
    }

    public ChecklistItemDetail getChecklistItemDetail() {
        return checklistItemDetail;
    }

    public void setChecklistItemDetail(ChecklistItemDetail checklistItemDetail) {
        this.checklistItemDetail = checklistItemDetail;
    }

    public boolean isSyncDisabled() {
        return syncDisabled;
    }

    @Override
    public void setSyncDisabledDateTime(Date syncDisabledDateTime) {
        this.syncDisabledDateTime = syncDisabledDateTime;
    }

    public void setSyncDisabled(boolean syncDisabled) {
        this.syncDisabled = syncDisabled;
    }

    @Override
    public Date getSyncDisabledDateTime() {
        return this.syncDisabledDateTime;
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getChecklist().getProgramEnrolment().getIndividual());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getChecklist().getProgramEnrolment().getIndividual());
    }
}
