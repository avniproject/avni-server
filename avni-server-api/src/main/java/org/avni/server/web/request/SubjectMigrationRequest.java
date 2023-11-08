package org.avni.server.web.request;

import java.util.List;
import java.util.Map;

public class SubjectMigrationRequest {
    private Map<String, String> destinationAddresses;
    private List<Long> subjectTypeIds;

    public List<Long> getSubjectTypeIds() {
        return subjectTypeIds;
    }

    public void setSubjectTypeIds(List<Long> subjectTypeIds) {
        this.subjectTypeIds = subjectTypeIds;
    }

    public Map<String, String> getDestinationAddresses() {
        return destinationAddresses;
    }

    public void setDestinationAddresses(Map<String, String> destinationAddresses) {
        this.destinationAddresses = destinationAddresses;
    }
}
