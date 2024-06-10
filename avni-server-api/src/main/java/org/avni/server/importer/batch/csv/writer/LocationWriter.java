package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormType;
import org.avni.server.builder.BuilderException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.importer.batch.csv.creator.LocationCreator;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.FormService;
import org.avni.server.service.ImportLocationsConstants;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationService;
import org.avni.server.util.S;
import org.avni.server.web.request.LocationContract;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;


@StepScope
@Component
public class LocationWriter implements ItemWriter<Row> {

    private static final LocationHeaders headers = new LocationHeaders();
    @Value("#{jobParameters['locationUploadMode']}")
    private String locationUploadMode;
    @Value("#{jobParameters['locationHierarchy']}")
    private String locationHierarchy;
    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final LocationCreator locationCreator;
    private final ObservationCreator observationCreator;
    private final ImportService importService;
    private final FormService formService;
    private List<String> locationTypeNames;

    @Autowired
    public LocationWriter(LocationService locationService,
                          LocationRepository locationRepository,
                          AddressLevelTypeRepository addressLevelTypeRepository,
                          ObservationCreator observationCreator,
                          ImportService importService,
                          FormService formService) {
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.observationCreator = observationCreator;
        this.importService = importService;
        this.formService = formService;
        this.locationCreator = new LocationCreator();
    }

    @PostConstruct
    public void init() {
        List<AddressLevelType> locationTypes = addressLevelTypeRepository.findAllByIsVoidedFalse();
        locationTypes.sort(Comparator.comparingDouble(AddressLevelType::getLevel).reversed());
        this.locationTypeNames = locationTypes.stream().map(AddressLevelType::getName).collect(Collectors.toList());
    }

    @Override
    public void write(List<? extends Row> rows) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        if (LocationUploadMode.isCreateMode(locationUploadMode)) {
            validateCreateModeHeaders(rows.get(0).getHeaders(), allErrorMsgs);
        } else {
            validateEditModeHeaders(rows.get(0).getHeaders(), allErrorMsgs);
        }
        for (Row row : rows) {
            if (LocationUploadMode.isCreateMode(locationUploadMode)) {
                createLocationWriter(row, allErrorMsgs);
            } else {
                editLocationWriter(row, allErrorMsgs);
            }
        }
    }

    private void editLocationWriter(Row row, List<String> allErrorMsgs) throws Exception {
        String existingLocationTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY);
        if (existingLocationTitleLineage.equalsIgnoreCase(ImportLocationsConstants.LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION)) return;
        String newLocationParentTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY);

        Optional<AddressLevel> existingLocationAddressLevel = locationRepository.findByTitleLineageIgnoreCase(existingLocationTitleLineage);
        if (!existingLocationAddressLevel.isPresent()) {
            allErrorMsgs.add(String.format("Provided Location does not exist in Avni. Please add it or check for spelling mistakes '%s'", existingLocationTitleLineage));
            throw new Exception(String.join(", ", allErrorMsgs));
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

    private void createLocationWriter(Row row, List<String> allErrorMsgs) throws Exception {
        AddressLevel parent = null;
        AddressLevel location = null;
        for (String header : row.getHeaders()) {
            if (isValidLocation(header, row, this.locationTypeNames)) {
                location = createAddressLevel(row, parent, header);
                parent = location;
            } //This will get called only when location have extra properties
            if (location != null && !this.locationTypeNames.contains(header)) {
                updateLocationProperties(row, allErrorMsgs, location);
            }
        }
    }

    private void updateLocationProperties(Row row, List<String> allErrorMsgs, AddressLevel location) throws Exception {
        location.setGpsCoordinates(locationCreator.getLocation(row, LocationHeaders.gpsCoordinates, allErrorMsgs));
        location.setLocationProperties(observationCreator.getObservations(row, headers, allErrorMsgs, FormType.Location, location.getLocationProperties()));
        locationRepository.save(location);
    }

    private void validateCreateModeHeaders(String[] headers, List<String> allErrorMsgs) throws Exception {
        List<String> headerList = Arrays.asList(headers);
        List<String> locationTypeHeaders = checkIfHeaderHasLocationTypesInOrderForHierarchy(this.locationHierarchy, headerList, allErrorMsgs);
        System.out.println(locationTypeHeaders);
        List<String> additionalHeaders = new ArrayList<>(headerList.subList(locationTypeHeaders.size(), headerList.size()));
        System.out.println(additionalHeaders);
        checkIfHeaderRowHasUnknownHeaders(additionalHeaders, allErrorMsgs);
    }

    private void validateEditModeHeaders(String[] headers, List<String> allErrorMsgs) throws Exception {
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
            throw new Exception(String.join(", ", allErrorMsgs));
        }
    }

    private List<String> checkIfHeaderHasLocationTypesInOrderForHierarchy(String locationHierarchy, List<String> headerList, List<String> allErrorMsgs) throws Exception {
        // There can be location properties in the header, so we save other values as locationProperties
        List<String> locationTypeNamesForHierachy = importService.getAddressLevelTypesForCreateModeSingleHierarchy(locationHierarchy)
            .stream().map(AddressLevelType::getName).collect(Collectors.toList());
        this.locationTypeNames = locationTypeNamesForHierachy;

        if (headerList.size() >= locationTypeNamesForHierachy.size() && !headerList.subList(0, locationTypeNamesForHierachy.size()).equals(locationTypeNamesForHierachy)) {
            allErrorMsgs.add("Location types missing in header for specified Location Hierarchy. Please refer to sample file for valid list of headers.");
            throw new Exception(String.join(", ", allErrorMsgs));
        }
        return locationTypeNamesForHierachy;
    }

    private void checkIfHeaderRowHasUnknownHeaders(List<String> additionalHeaders, List<String> allErrorMsgs) throws Exception {
        additionalHeaders.removeIf(StringUtils::isEmpty);
        if (!additionalHeaders.isEmpty()) {
            List<String> locationPropertyNames = formService.getFormElementNamesForLocationTypeForms()
                .stream().map(FormElement::getName).collect(Collectors.toList());
            locationPropertyNames.add(LocationHeaders.gpsCoordinates);
            if ((!locationPropertyNames.containsAll(additionalHeaders))) {
                allErrorMsgs.add("Unknown headers included in file. Please refer to sample file for valid list of headers.");
                throw new Exception(String.join(", ", allErrorMsgs));
            }
        }
    }

    private AddressLevel createAddressLevel(Row row, AddressLevel parent, String header) throws BuilderException {
        AddressLevel location;
        location = locationRepository.findChildLocation(parent, row.get(header));
        if (location == null) {
            LocationContract locationContract = new LocationContract();
            locationContract.setupUuidIfNeeded();
            locationContract.setName(row.get(header));
            locationContract.setType(header);
            locationContract.setLevel(parent == null ? this.locationTypeNames.size() : parent.getLevel() - 1);
            if (parent != null) {
                locationContract.setParent(new LocationContract(parent.getUuid()));
            }
            location = locationService.save(locationContract);
        }
        return location;
    }

    private boolean isValidLocation(String header, Row row, List<String> locationTypeNames) {
        return locationTypeNames.contains(header) && !S.isEmpty(row.get(header));
    }

    private void updateExistingLocation(AddressLevel location, AddressLevel newParent, Row row, List<String> allErrorMsgs) throws Exception {
        String newTitle = row.get(ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME);
        if (!StringUtils.isEmpty(newTitle)) location.setTitle(newTitle);
        if (newParent != null) {
            locationService.updateParent(location, newParent);
        }
        updateLocationProperties(row, allErrorMsgs, location);
    }

    public void setLocationUploadMode(String locationUploadMode) {
        this.locationUploadMode = locationUploadMode;
    }

    public void setLocationHierarchy(String locationHierarchy) {
        this.locationHierarchy = locationHierarchy;
    }

    public enum LocationUploadMode {
        CREATE, EDIT;

        public static boolean isCreateMode(String mode) {
            return mode == null || LocationUploadMode.valueOf(mode).equals(CREATE);
        }

        public static boolean isCreateMode(LocationUploadMode mode) {
            return mode.equals(CREATE);
        }
    }
}
