package org.avni.server.web.request;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SubjectMigrationRequest implements Serializable {
    private List<String> subjectUuids;
    private Map<String, String> destinationAddresses;
    private Map<String, String> destinationSyncConcepts;

    public List<String> getSubjectUuids() {
        return subjectUuids;
    }

    public void setSubjectUuids(List<String> subjectUuids) {
        this.subjectUuids = subjectUuids;
    }

    public Map<String, String> getDestinationAddresses() {
        return destinationAddresses;
    }

    public void setDestinationAddresses(Map<String, String> destinationAddresses) {
        this.destinationAddresses = destinationAddresses;
    }

    public Map<String, String> getDestinationSyncConcepts() {
        return destinationSyncConcepts;
    }

    public void setDestinationSyncConcepts(Map<String, String> destinationSyncConcepts) {
        this.destinationSyncConcepts = destinationSyncConcepts;
    }
}
