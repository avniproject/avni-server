package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "checklist_item")
@JsonIgnoreProperties({"checklist"})
@BatchSize(size = 100)
public class ChecklistItem extends OrganisationAwareEntity {
    @Column
    private Instant completionDate;

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

    public DateTime getCompletionDate() {
        return DateTimeUtil.toJodaDateTime(completionDate);
    }

    public void setCompletionDate(DateTime completionDate) {
        this.completionDate = DateTimeUtil.toInstant(completionDate);
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
}
