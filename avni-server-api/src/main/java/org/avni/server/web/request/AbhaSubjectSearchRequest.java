package org.avni.server.web.request;

// POST /abha/subjects/search. No toString: the number must not reach a log.
public class AbhaSubjectSearchRequest {
    private String subjectTypeUUID;
    private String abhaNumber;
    // An ABHA made with a mobile number only has an address and no number.
    private String abhaAddress;

    public String getSubjectTypeUUID() {
        return subjectTypeUUID;
    }

    public void setSubjectTypeUUID(String subjectTypeUUID) {
        this.subjectTypeUUID = subjectTypeUUID;
    }

    public String getAbhaNumber() {
        return abhaNumber;
    }

    public void setAbhaNumber(String abhaNumber) {
        this.abhaNumber = abhaNumber;
    }

    public String getAbhaAddress() {
        return abhaAddress;
    }

    public void setAbhaAddress(String abhaAddress) {
        this.abhaAddress = abhaAddress;
    }
}
