package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LocationHeaderCreator implements HeaderCreator {
    public final static String gpsCoordinates = "GPS coordinates";
    private final AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    public LocationHeaderCreator(AddressLevelTypeRepository addressLevelTypeRepository) {
        this.addressLevelTypeRepository = addressLevelTypeRepository;
    }

    @Override
    public String[] getAllHeaders() {
        return new String[]{gpsCoordinates};
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping) {
        return getAllHeaders();
    }

    @Override
    public String[] getConceptHeaders(FormMapping formMapping, String[] fileHeaders) {
        List<AddressLevelType> locationTypes = addressLevelTypeRepository.findAll();
        ArrayList<String> allKnownNonConceptHeaders = locationTypes.stream().map(AddressLevelType::getName).collect(Collectors.toCollection(ArrayList::new));
        allKnownNonConceptHeaders.add(gpsCoordinates);

        return Arrays
                .stream(fileHeaders)
                .filter(header -> !allKnownNonConceptHeaders.contains(header)).toArray(String[]::new);
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping) {
        return new String[0];
    }
}
