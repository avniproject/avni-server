package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormElement;
import org.avni.server.builder.BuilderException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.FormService;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationService;
import org.avni.server.util.S;
import org.avni.server.web.request.LocationContract;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// This class is need so that the logic can be instantiated in integration tests. Spring batch configuration is not working in integration tests.
@Component
public class BulkLocationCreator extends BulkLocationModifier {
    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final ImportService importService;
    private final FormService formService;
    public static final String LocationTypesHeaderError = "Location types missing or not in order in header for specified Location Hierarchy. Please refer to sample file for valid list of headers.";
    public static final String UnknownHeadersErrorMessage = "Unknown headers included in file. Please refer to sample file for valid list of headers.";

    public BulkLocationCreator(LocationService locationService, LocationRepository locationRepository, AddressLevelTypeRepository addressLevelTypeRepository, ObservationCreator observationCreator, ImportService importService, FormService formService) {
        super(locationRepository, observationCreator);
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.importService = importService;
        this.formService = formService;
    }

    public List<String> getLocationTypeNames() {
        List<AddressLevelType> locationTypes = addressLevelTypeRepository.findAllByIsVoidedFalse();
        locationTypes.sort(Comparator.comparingDouble(AddressLevelType::getLevel).reversed());
        return locationTypes.stream().map(AddressLevelType::getName).collect(Collectors.toList());
    }

    public void createLocation(Row row, List<String> allErrorMsgs, List<String> locationTypeNames) {
        AddressLevel parent = null;
        AddressLevel location = null;
        for (String columnHeader : row.getHeaders()) {
            if (isValidLocation(columnHeader, row, locationTypeNames)) {
                location = createAddressLevel(row, parent, columnHeader, locationTypeNames);
                parent = location;
            } //This will get called only when location have extra properties
            if (location != null && !locationTypeNames.contains(columnHeader)) {
                updateLocationProperties(row, allErrorMsgs, location);
            }
        }
    }

    private List<String> validateCreateModeHeaders(String[] headers, List<String> allErrorMsgs, String locationHierarchy) {
        List<String> headerList = Arrays.asList(headers);
        List<String> locationTypeHeaders = checkIfHeaderHasLocationTypesInOrderForHierarchy(locationHierarchy, headerList, allErrorMsgs);
        List<String> additionalHeaders = new ArrayList<>(headerList.subList(locationTypeHeaders.size(), headerList.size()));
        checkIfHeaderRowHasUnknownHeaders(additionalHeaders, allErrorMsgs);
        return locationTypeHeaders;
    }

    private List<String> checkIfHeaderHasLocationTypesInOrderForHierarchy(String locationHierarchy, List<String> headerList, List<String> allErrorMsgs) {
        List<String> locationTypeNamesForHierachy = importService.getAddressLevelTypesForCreateModeSingleHierarchy(locationHierarchy)
                .stream().map(AddressLevelType::getName).collect(Collectors.toList());

        if (headerList.size() >= locationTypeNamesForHierachy.size() && !headerList.subList(0, locationTypeNamesForHierachy.size()).equals(locationTypeNamesForHierachy)) {
            allErrorMsgs.add(LocationTypesHeaderError);
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
        return locationTypeNamesForHierachy;
    }

    private void checkIfHeaderRowHasUnknownHeaders(List<String> additionalHeaders, List<String> allErrorMsgs) {
        additionalHeaders.removeIf(StringUtils::isEmpty);
        if (!additionalHeaders.isEmpty()) {
            List<String> locationPropertyNames = formService.getFormElementNamesForLocationTypeForms()
                    .stream().map(FormElement::getName).collect(Collectors.toList());
            locationPropertyNames.add(LocationHeaders.gpsCoordinates);
            if ((!locationPropertyNames.containsAll(additionalHeaders))) {
                allErrorMsgs.add(UnknownHeadersErrorMessage);
                throw new RuntimeException(String.join(", ", allErrorMsgs));
            }
        }
    }

    private AddressLevel createAddressLevel(Row row, AddressLevel parent, String header, List<String> locationTypeNames) throws BuilderException {
        AddressLevel location;
        location = locationRepository.findChildLocation(parent, row.get(header));
        if (location == null) {
            LocationContract locationContract = new LocationContract();
            locationContract.setupUuidIfNeeded();
            locationContract.setName(row.get(header));
            locationContract.setType(header);
            locationContract.setLevel(parent == null ? locationTypeNames.size() : parent.getLevel() - 1);
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

    public void write(List<? extends Row> rows, String locationHierarchy) {
        List<String> allErrorMsgs = new ArrayList<>();
        List<String> locationTypeNames = validateCreateModeHeaders(rows.get(0).getHeaders(), allErrorMsgs, locationHierarchy);
        for (Row row : rows) {
            createLocation(row, allErrorMsgs, locationTypeNames);
        }
    }
}
