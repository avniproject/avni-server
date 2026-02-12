package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;
import org.avni.server.importer.batch.model.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AddressLevelCreator {
    private final LocationRepository locationRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    public AddressLevelCreator(LocationRepository locationRepository, AddressLevelTypeRepository addressLevelTypeRepository) {
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
    }

    public AddressLevel findAddressLevel(Row row,
                                         AddressLevelTypes addressLevelTypes) {
        AddressLevelTypes orderedLocationTypes = addressLevelTypes.getLowToHigh();
        AddressLevelType firstMatch = orderedLocationTypes.stream()
                .filter(addressLevelType -> row.get(addressLevelType.getName()) != null && !StringUtils.isEmpty(row.get(addressLevelType.getName()).trim()))
                .findFirst()
                .orElse(null);
        if (firstMatch == null) {
            String availableTypes = orderedLocationTypes.stream()
                    .map(AddressLevelType::getName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            
            String errorMessage = availableTypes.isEmpty()
                    ? "No address levels setup in the organisation"
                    : String.format("None of the expected address levels provided. Expected one of: [%s]", availableTypes);
            
            throw new RuntimeException(errorMessage);
        }

        String title = row.get(firstMatch.getName());
        List<AddressLevel> matchingAddressLevels = locationRepository.findByTitleIgnoreCaseAndType(title, firstMatch, PageRequest.of(0, 2));
        switch (matchingAddressLevels.size()) {
            case 0:
                List<String> parentLineage = new ArrayList<>();
                AddressLevelType currentParent = firstMatch.getParent();
                while (currentParent != null) {
                    parentLineage.add(row.get(currentParent.getName()));
                    currentParent = currentParent.getParent();
                }
                
                String errorMessage = parentLineage.isEmpty() 
                    ? String.format("The %s '%s' is not set up in Avni", firstMatch.getName(), title)
                    : String.format("The %s '%s' is not set up in Avni within '%s'", 
                        firstMatch.getName(), title, String.join(" , ", parentLineage));
                
                throw new RuntimeException(errorMessage);
            case 1:
                return matchingAddressLevels.getFirst();
            default:
                return getAddressLevelByLineage(row, new AddressLevelTypes(addressLevelTypeRepository.getAllAddressLevelTypes()));
        }
    }

    private AddressLevel getAddressLevelByLineage(Row row,
                                                  AddressLevelTypes locationTypes) {
        AddressLevelTypes highToLow = locationTypes.getHighToLow();
        List<String> inputLocations = new ArrayList<>();
        for (AddressLevelType addressLevelType : highToLow) {
            String _location = row.get(addressLevelType.getName());
            if (StringUtils.hasText(_location))
                inputLocations.add(_location);
        }

        if (inputLocations.isEmpty())
            throw new RuntimeException("No locations matching their location types found.");

        String lineage = String.join(", ", inputLocations);

        return locationRepository.findByTitleLineageIgnoreCase(lineage)
                .orElseThrow(() -> new RuntimeException("'Address' not found: " + lineage));
    }
}
