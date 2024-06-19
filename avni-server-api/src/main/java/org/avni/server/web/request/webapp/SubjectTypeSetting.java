package org.avni.server.web.request.webapp;

import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;

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
        List<AddressLevelType> matching = addressLevelTypes.stream().filter(locationType -> locationTypeUUIDs.contains(locationType.getUuid())).collect(Collectors.toList());
        return new AddressLevelTypes(matching);
    }
}
