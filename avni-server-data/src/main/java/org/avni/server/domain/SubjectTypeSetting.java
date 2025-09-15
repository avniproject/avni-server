package org.avni.server.domain;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class SubjectTypeSetting implements Serializable {
    private String subjectTypeUUID;
    private List<String> locationTypeUUIDs;

    public String getSubjectTypeUUID() {
        return subjectTypeUUID;
    }

    public void setSubjectTypeUUID(String subjectTypeUUID) {
        this.subjectTypeUUID = subjectTypeUUID;
    }

    public List<String> getLocationTypeUUIDs() {
        return locationTypeUUIDs;
    }

    public void setLocationTypeUUIDs(List<String> locationTypeUUIDs) {
        this.locationTypeUUIDs = locationTypeUUIDs;
    }

    public AddressLevelTypes getAddressLevelTypes(AddressLevelTypes addressLevelTypes) {
        List<AddressLevelType> matching = addressLevelTypes.stream().filter(locationType -> !locationType.isVoided() && locationTypeUUIDs.contains(locationType.getUuid())).collect(Collectors.toList());
        return new AddressLevelTypes(matching);
    }
}
