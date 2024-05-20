package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;
import org.avni.server.importer.batch.model.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AddressLevelCreator {
    private final LocationRepository locationRepository;

    @Autowired
    public AddressLevelCreator(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public AddressLevel findAddressLevel(Row row,
                                         AddressLevelTypes addressLevelTypes) throws Exception {
        AddressLevelTypes orderedLocationTypes = addressLevelTypes.getLowToHigh();
        AddressLevelType firstMatch = orderedLocationTypes.stream().filter(addressLevelType -> row.get(addressLevelType.getName()) != null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No matching location types found. If subject type has registration locations then only those will be used for matching."));

        String title = row.get(firstMatch.getName());
        List<AddressLevel> matchingAddressLevels = locationRepository.findByTitleAndType(title, firstMatch, PageRequest.of(0, 2));
        switch (matchingAddressLevels.size()) {
            case 0:
                throw new RuntimeException(("Address not found: " + title));
            case 1:
                return matchingAddressLevels.get(0);
            default:
                return getAddressLevelByLineage(row, addressLevelTypes);
        }
    }

    private AddressLevel getAddressLevelByLineage(Row row,
                                                  AddressLevelTypes locationTypes) {
        AddressLevelTypes highToLow = locationTypes.getHighToLow();
        List<String> inputLocations = new ArrayList<>();
        for (AddressLevelType addressLevelType : highToLow) {
            String _location = row.get(addressLevelType.getName());
            if (_location != null)
                inputLocations.add(_location);
        }

        if (inputLocations.isEmpty())
            throw new RuntimeException("No locations matching their location types found.");

        String lineage = String.join(", ", inputLocations);

        return locationRepository.findByTitleLineageIgnoreCase(lineage)
                .orElseThrow(() -> new RuntimeException("'Address' not found: " + lineage));
    }
}
