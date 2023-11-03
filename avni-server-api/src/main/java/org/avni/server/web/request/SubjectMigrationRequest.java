package org.avni.server.web.request;

import java.util.List;
import java.util.Map;

public class SubjectMigrationRequest {
    private Map<String, String> destinationAddresses;
    private long sourceAddressTypeId;
    private long destAddressTypeId;
    private List<Long> subjectTypeIds;

    public long getSourceAddressTypeId() {
        return sourceAddressTypeId;
    }

    public void setSourceAddressTypeId(long sourceAddressTypeId) {
        this.sourceAddressTypeId = sourceAddressTypeId;
    }

    public long getDestAddressTypeId() {
        return destAddressTypeId;
    }

    public void setDestAddressTypeId(long destAddressTypeId) {
        this.destAddressTypeId = destAddressTypeId;
    }

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
