package org.avni.server.web.response;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.service.ConceptService;

import java.util.LinkedHashMap;

public class LocationApiResponse extends LinkedHashMap<String, Object> {
    public static LocationApiResponse fromAddressLevel(AddressLevel addressLevel, ConceptRepository conceptRepository, ConceptService conceptService) {
        LocationApiResponse locationApiResponse = new LocationApiResponse();
        locationApiResponse.put("ID", addressLevel.getUuid());
        locationApiResponse.put("External ID", addressLevel.getLegacyId());
        locationApiResponse.put("Title", addressLevel.getTitle());
        locationApiResponse.put("Type", addressLevel.getType().getName());
        locationApiResponse.put("Level", addressLevel.getLevel());
        Response.putIfPresent(locationApiResponse, "GPS Coordinates", addressLevel.getGpsCoordinates());
        Response.putObservations(conceptRepository, conceptService, locationApiResponse, new LinkedHashMap<>(), addressLevel.getLocationProperties(), "customProperties");
        if (addressLevel.getParent() != null) {
            locationApiResponse.put("Parent", LocationApiResponse.fromAddressLevel(addressLevel.getParent(), conceptRepository, conceptService));
        }
        locationApiResponse.put("Voided", addressLevel.isVoided());
        Response.putAudit(addressLevel, locationApiResponse);
        return locationApiResponse;
    }
}
