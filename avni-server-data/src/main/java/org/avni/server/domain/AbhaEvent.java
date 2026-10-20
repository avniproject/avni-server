package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;

// One ABHA action: created or linked (sent by the phone), removed (written by the server). The audit record itself.
@Entity
@Table(name = "abha_event")
@BatchSize(size = 100)
public class AbhaEvent extends OrganisationAwareEntity {
    // No foreign key: the subject may not have synced yet.
    @NotNull
    @Column(name = "subject_uuid")
    private String subjectUuid;

    @NotNull
    @Column(name = "user_id")
    private Long userId;

    @NotNull
    @Column(name = "hip_id")
    private String hipId;

    @NotNull
    @Column(name = "action")
    private String action;

    @NotNull
    @Column(name = "route")
    private String route;

    @Column(name = "abha_number")
    private String abhaNumber;

    @Column(name = "abha_address")
    private String abhaAddress;

    @Column(name = "eka_oid")
    private String ekaOid;

    @Column(name = "eka_txn_id")
    private String ekaTxnId;

    @Column(name = "consent_given_at")
    private Instant consentGivenAt;

    @NotNull
    @Column(name = "occurred_at")
    private Instant occurredAt;

    public String getSubjectUuid() { return subjectUuid; }
    public void setSubjectUuid(String subjectUuid) { this.subjectUuid = subjectUuid; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getHipId() { return hipId; }
    public void setHipId(String hipId) { this.hipId = hipId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getAbhaNumber() { return abhaNumber; }
    public void setAbhaNumber(String abhaNumber) { this.abhaNumber = abhaNumber; }
    public String getAbhaAddress() { return abhaAddress; }
    public void setAbhaAddress(String abhaAddress) { this.abhaAddress = abhaAddress; }
    public String getEkaOid() { return ekaOid; }
    public void setEkaOid(String ekaOid) { this.ekaOid = ekaOid; }
    public String getEkaTxnId() { return ekaTxnId; }
    public void setEkaTxnId(String ekaTxnId) { this.ekaTxnId = ekaTxnId; }
    public Instant getConsentGivenAt() { return consentGivenAt; }
    public void setConsentGivenAt(Instant consentGivenAt) { this.consentGivenAt = consentGivenAt; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
