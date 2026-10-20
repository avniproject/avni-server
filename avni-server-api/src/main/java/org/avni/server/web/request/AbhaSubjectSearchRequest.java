package org.avni.server.web.request;

// POST /abha/subjects/search. No toString: the number must not reach a log.
public class AbhaSubjectSearchRequest {
    private String subjectTypeUUID;
    private String abhaNumber;

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
}
