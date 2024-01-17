package org.avni.server.web.request.api;

import java.util.ArrayList;
import java.util.List;

public class DeleteSubjectCriteria {
    private List<Long> addressIds = new ArrayList<>();

    public List<Long> getAddressIds() {
        return addressIds;
    }

    public void setAddressIds(List<Long> addressIds) {
        this.addressIds = addressIds;
    }
}
