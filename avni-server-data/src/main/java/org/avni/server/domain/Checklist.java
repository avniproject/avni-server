package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "checklist")
@JsonIgnoreProperties({"items", "programEnrolment"})
@BatchSize(size = 100)
public class Checklist extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "checklist_detail_id")
    private ChecklistDetail checklistDetail;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrolment_id")
    private ProgramEnrolment programEnrolment;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "checklist")
    private List<ChecklistItem> items = new ArrayList<>();

    @NotNull
    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime baseDate;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public ChecklistDetail getChecklistDetail() {
        return checklistDetail;
    }

    public void setChecklistDetail(ChecklistDetail checklistDetail) {
        this.checklistDetail = checklistDetail;
    }

    public ProgramEnrolment getProgramEnrolment() {
        return programEnrolment;
    }

    public void setProgramEnrolment(ProgramEnrolment programEnrolment) {
        this.programEnrolment = programEnrolment;
    }

    public DateTime getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(DateTime baseDate) {
        this.baseDate = baseDate;
    }

    public List<ChecklistItem> getItems() {
        return items;
    }

    public void setItems(List<ChecklistItem> items) {
        this.items = items;
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

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getProgramEnrolment().getIndividual());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getProgramEnrolment().getIndividual());
    }
}
