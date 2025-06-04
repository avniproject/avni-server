package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

@Entity
@Table(name = "group_subject")
@JsonIgnoreProperties({"groupSubject", "memberSubject", "groupRole"})
@BatchSize(size = 100)
public class GroupSubject extends OrganisationAwareEntity implements SubjectLinkedSyncEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_subject_id")
    private Individual groupSubject;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_subject_id")
    private Individual memberSubject;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_role_id")
    private GroupRole groupRole;

    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime membershipStartDate;

    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime membershipEndDate;

    @Column(name = "member_subject_address_id")
    private Long memberSubjectAddressId;

    @Column(name = "group_subject_address_id")
    private Long groupSubjectAddressId;

    @Column(name = "group_subject_sync_concept_1_value")
    private String groupSubjectSyncConcept1Value;

    @Column(name = "group_subject_sync_concept_2_value")
    private String groupSubjectSyncConcept2Value;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public Individual getGroupSubject() {
        return groupSubject;
    }

    public void setGroupSubject(Individual groupSubject) {
        this.groupSubject = groupSubject;
    }

    public Individual getMemberSubject() {
        return memberSubject;
    }

    public void setMemberSubject(Individual memberSubject) {
        this.memberSubject = memberSubject;
    }

    public GroupRole getGroupRole() {
        return groupRole;
    }

    public void setGroupRole(GroupRole groupRole) {
        this.groupRole = groupRole;
    }

    public DateTime getMembershipStartDate() {
        return membershipStartDate;
    }

    public void setMembershipStartDate(DateTime membershipStartDate) {
        this.membershipStartDate = membershipStartDate;
    }

    public DateTime getMembershipEndDate() {
        return membershipEndDate;
    }

    public void setMembershipEndDate(DateTime membershipEndDate) {
        this.membershipEndDate = membershipEndDate;
    }

    public String getGroupSubjectUUID() {
        return groupSubject.getUuid();
    }

    public String getMemberSubjectUUID() {
        return memberSubject.getUuid();
    }

    public String getGroupRoleUUID() {
        return groupRole.getUuid();
    }

    @JsonIgnore
    public Long getMemberSubjectAddressId() {
        return memberSubjectAddressId;
    }

    public void setMemberSubjectAddressId(Long memberSubjectAddressId) {
        this.memberSubjectAddressId = memberSubjectAddressId;
    }

    @JsonIgnore
    public Long getGroupSubjectAddressId() {
        return groupSubjectAddressId;
    }

    public void setGroupSubjectAddressId(Long groupSubjectAddressId) {
        this.groupSubjectAddressId = groupSubjectAddressId;
    }

    @JsonIgnore
    public String getGroupSubjectSyncConcept1Value() {
        return groupSubjectSyncConcept1Value;
    }

    public void setGroupSubjectSyncConcept1Value(String groupSubjectSyncConcept1Value) {
        this.groupSubjectSyncConcept1Value = groupSubjectSyncConcept1Value;
    }

    @JsonIgnore
    public String getGroupSubjectSyncConcept2Value() {
        return groupSubjectSyncConcept2Value;
    }

    public void setGroupSubjectSyncConcept2Value(String groupSubjectSyncConcept2Value) {
        this.groupSubjectSyncConcept2Value = groupSubjectSyncConcept2Value;
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
        SyncDisabledEntityHelper.handleSave(this, this.getGroupSubject(), this.getMemberSubject());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getGroupSubject(), this.getMemberSubject());
    }
}
