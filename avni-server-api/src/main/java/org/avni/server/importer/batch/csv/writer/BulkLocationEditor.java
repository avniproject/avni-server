package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ImportLocationsConstants;
import org.avni.server.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class BulkLocationEditor extends BulkLocationModifier {
    private final LocationService locationService;

    @Autowired
    public BulkLocationEditor(LocationRepository locationRepository, ObservationCreator observationCreator, LocationService locationService) {
        super(locationRepository, observationCreator);
        this.locationService = locationService;
    }

    public void editLocation(Row row, List<String> allErrorMsgs) {
        String existingLocationTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY);
        if (existingLocationTitleLineage.equalsIgnoreCase(ImportLocationsConstants.LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION)) return;
        String newLocationParentTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY);

        Optional<AddressLevel> existingLocationAddressLevel = locationRepository.findByTitleLineageIgnoreCase(existingLocationTitleLineage);
        if (!existingLocationAddressLevel.isPresent()) {
            allErrorMsgs.add(String.format("Provided Location does not exist in Avni. Please add it or check for spelling mistakes '%s'", existingLocationTitleLineage));
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }

        AddressLevel newLocationParentAddressLevel = null;
        if (!StringUtils.isEmpty(newLocationParentTitleLineage)) {
            newLocationParentAddressLevel = locationRepository.findByTitleLineageIgnoreCase(newLocationParentTitleLineage).orElse(null);
            if (newLocationParentAddressLevel == null) {
                allErrorMsgs.add(String.format("Provided new Location parent does not exist in Avni. Please add it or check for spelling mistakes '%s'", newLocationParentTitleLineage));
            }
        }
        updateExistingLocation(existingLocationAddressLevel.get(), newLocationParentAddressLevel, row, allErrorMsgs);
    }

    public void validateEditModeHeaders(String[] headers, List<String> allErrorMsgs) {
        List<String> headerList = Arrays.asList(headers);
        if (!headerList.contains(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY)) {
            allErrorMsgs.add(String.format("'%s' is required", ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY));
        }
        if (!(headerList.contains(ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME)
                || headerList.contains(ImportLocationsConstants.COLUMN_NAME_GPS_COORDINATES)
                || headerList.contains(ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY))) {
            allErrorMsgs.add(String.format("At least one of '%s', '%s' or '%s' is required", ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME, ImportLocationsConstants.COLUMN_NAME_GPS_COORDINATES, ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY));
        }
        if (!allErrorMsgs.isEmpty()) {
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
    }

    private void updateExistingLocation(AddressLevel location, AddressLevel newParent, Row row, List<String> allErrorMsgs) {
        String newTitle = row.get(ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME);
        if (!StringUtils.isEmpty(newTitle)) location.setTitle(newTitle);
        if (newParent != null) {
            locationService.updateParent(location, newParent);
        }
        updateLocationProperties(row, allErrorMsgs, location);
    }

    public void write(List<? extends Row> rows) {
        List<String> allErrorMsgs = new ArrayList<>();
        validateEditModeHeaders(rows.get(0).getHeaders(), allErrorMsgs);
        for (Row row : rows) {
            editLocation(row, allErrorMsgs);
        }
    }
}
