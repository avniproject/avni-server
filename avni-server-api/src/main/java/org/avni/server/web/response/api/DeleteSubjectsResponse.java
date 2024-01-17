package org.avni.server.web.response.api;

import org.avni.server.domain.AddressLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteSubjectsResponse {
    private Map<Long, String> locations = new HashMap<>();
    private List<Long> notFoundAddresses = new ArrayList<>();

    public void addCompletedAddress(AddressLevel addressLevel) {
        locations.put(addressLevel.getId(), addressLevel.getTitle());
    }

    public void addNotFoundAddress(Long addressId) {
        notFoundAddresses.add(addressId);
    }

    public Map<Long, String> getLocations() {
        return locations;
    }

    public List<Long> getNotFoundAddresses() {
        return notFoundAddresses;
    }
}
