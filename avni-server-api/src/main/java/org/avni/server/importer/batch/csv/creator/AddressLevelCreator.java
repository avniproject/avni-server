package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.importer.batch.model.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AddressLevelCreator {

    private LocationRepository locationRepository;

    @Autowired
    public AddressLevelCreator(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public AddressLevel findAddressLevel(Row row,
                                         List<AddressLevelType> locationTypes) throws Exception {
        AddressLevelType lowestAddressLevelType = locationTypes.get(locationTypes.size() - 1);

        String lowestInputAddressLevel = row.get(lowestAddressLevelType.getName());
        if (lowestInputAddressLevel == null) {
            throw new Exception(String.format("Missing '%s'", lowestAddressLevelType.getName()));
        }

        List<AddressLevel> matchingAddressLevels = locationRepository.findByTitleAndType(lowestInputAddressLevel, lowestAddressLevelType, PageRequest.of(0, 2));
        switch (matchingAddressLevels.size()) {
            case 0:
                throw new Exception(("Address not found: " + lowestInputAddressLevel));
            case 1:
                return matchingAddressLevels.get(0);
            default:
                return getAddressLevelByLineage(row, locationTypes);
        }
    }

    private AddressLevel getAddressLevelByLineage(Row row,
                                                  List<AddressLevelType> locationTypes) throws Exception {
        List<String> inputLocations = new ArrayList<>();
        for (AddressLevelType addressLevelType : locationTypes) {
            String _location = row.get(addressLevelType.getName());
            if (_location != null)
                inputLocations.add(_location);
        }

        if (inputLocations.size() == 0)
            throw new Exception("Invalid address");

        String lineage = String.join(", ", inputLocations);

        return locationRepository.findByTitleLineageIgnoreCase(lineage)
                .orElseThrow(() -> new Exception("'Address' not found: " + lineage));
    }
}
