package org.avni.server.service.abha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// The body of POST /abha/events. Who sent it, and from which health centre, is never read from here.
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbhaEventRequest {
    private String uuid;
    private String subjectUUID;
    private String subjectTypeUUID;
    private String action;
    private String route;
    private String abhaNumber;
    private String abhaAddress;
    private String ekaOid;
    private String ekaTxnId;
    private String consentGivenAt;
    private String occurredAt;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getSubjectUUID() { return subjectUUID; }
    public void setSubjectUUID(String subjectUUID) { this.subjectUUID = subjectUUID; }
    public String getSubjectTypeUUID() { return subjectTypeUUID; }
    public void setSubjectTypeUUID(String subjectTypeUUID) { this.subjectTypeUUID = subjectTypeUUID; }
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
    public String getConsentGivenAt() { return consentGivenAt; }
    public void setConsentGivenAt(String consentGivenAt) { this.consentGivenAt = consentGivenAt; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
}
