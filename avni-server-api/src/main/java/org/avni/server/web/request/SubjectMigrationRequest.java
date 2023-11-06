package org.avni.server.web.request;

import java.util.List;
import java.util.Map;

public class SubjectMigrationRequest {
    private Map<Long, Long> destinationAddresses;
    private List<Long> subjectTypeIds;

    public List<Long> getSubjectTypeIds() {
        return subjectTypeIds;
    }

    public void setSubjectTypeIds(List<Long> subjectTypeIds) {
        this.subjectTypeIds = subjectTypeIds;
    }

    public Map<Long, Long> getDestinationAddresses() {
        return destinationAddresses;
    }

    public void setDestinationAddresses(Map<Long, Long> destinationAddresses) {
        this.destinationAddresses = destinationAddresses;
    }
}
