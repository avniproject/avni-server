package org.avni.server.importer.batch.csv.writer;

import com.google.common.collect.Sets;
import jakarta.transaction.Transactional;
import org.avni.server.builder.BuilderException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaderCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.FormService;
import org.avni.server.service.ImportLocationsConstants;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationService;
import org.avni.server.util.CollectionUtil;
import org.avni.server.util.S;
import org.avni.server.web.request.LocationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.util.*;
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
    public static final String ParentMissingOfLocation = "Parent missing for location provided";
    public static final String NoLocationProvided = "No location provided";

    @Autowired
    public BulkLocationCreator(LocationService locationService, LocationRepository locationRepository, AddressLevelTypeRepository addressLevelTypeRepository, ObservationCreator observationCreator, ImportService importService, FormService formService, LocationHeaderCreator locationHeaderCreator) {
        super(locationRepository, observationCreator, locationHeaderCreator);
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.importService = importService;
        this.formService = formService;
    }

    public void createLocation(Row row, List<String> allErrorMsgs, List<String> locationTypeNames) throws ValidationException {
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

    private List<String> validateHeaders(String[] headers, List<String> allErrorMsgs, String locationHierarchy) {
        List<String> headerList = Arrays.asList(headers);
        List<String> locationTypeHeaders = checkIfHeaderHasLocationTypesAndInOrderForHierarchy(locationHierarchy, headerList, allErrorMsgs);
        List<String> additionalHeaders = headerList.size() > locationTypeHeaders.size() ? new ArrayList<>(headerList.subList(locationTypeHeaders.size(), headerList.size())) : new ArrayList<>();
        checkIfHeaderRowHasUnknownHeaders(additionalHeaders, allErrorMsgs);
        return locationTypeHeaders;
    }

    private List<String> checkIfHeaderHasLocationTypesAndInOrderForHierarchy(String locationHierarchy, List<String> headerList, List<String> allErrorMsgs) {
        List<String> locationTypeNamesForHierarchy = importService.getAddressLevelTypesForCreateModeSingleHierarchy(locationHierarchy)
                .stream().map(AddressLevelType::getName).collect(Collectors.toList());

        HashSet<String> expectedHeaders = new HashSet<>(locationTypeNamesForHierarchy);
        if (Sets.difference(new HashSet<>(expectedHeaders), new HashSet<>(headerList)).size() == locationTypeNamesForHierarchy.size()) {
            allErrorMsgs.add(LocationTypesHeaderError);
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }

        if (headerList.size() >= locationTypeNamesForHierarchy.size() && !headerList.subList(0, locationTypeNamesForHierarchy.size()).equals(locationTypeNamesForHierarchy)) {
            allErrorMsgs.add(LocationTypesHeaderError);
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
        return locationTypeNamesForHierarchy;
    }

    private void checkIfHeaderRowHasUnknownHeaders(List<String> additionalHeaders, List<String> allErrorMsgs) {
        additionalHeaders.removeIf(StringUtils::isEmpty);
        if (!additionalHeaders.isEmpty()) {
            List<String> locationPropertyNames = formService.getFormElementNamesForLocationTypeForms()
                    .stream().map(formElement -> formElement.getConcept().getName()).collect(Collectors.toList());
            locationPropertyNames.add(LocationHeaderCreator.gpsCoordinates);
            if ((!locationPropertyNames.containsAll(additionalHeaders.stream().map(S::unDoubleQuote).toList()))) {
                allErrorMsgs.add(UnknownHeadersErrorMessage);
                throw new RuntimeException(String.join(", ", allErrorMsgs));
            }
        }
    }

    private AddressLevel createAddressLevel(Row row, AddressLevel parent, String header, List<String> locationTypeNames) throws BuilderException {
        AddressLevel location;
        AddressLevelType addressLevelType = addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse(header);
        location = locationRepository.findChildLocation(parent, addressLevelType, row.get(header));
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

    private void validateRow(Row row, List<String> hierarchicalLocationTypeNames, List<String> allErrorMsgs) {
        List<String> values = row.get(hierarchicalLocationTypeNames);
        if (CollectionUtil.isEmpty(values)) {
            allErrorMsgs.add(NoLocationProvided);
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
        if (!CollectionUtil.hasOnlyTrailingEmptyStrings(values)) {
            allErrorMsgs.add(ParentMissingOfLocation);
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void write(List<? extends Row> rows, String idBasedLocationHierarchy) throws ValidationException {
        try {
            List<String> allErrorMsgs = new ArrayList<>();
            List<String> hierarchicalLocationTypeNames = validateHeaders(rows.get(0).getHeaders(), allErrorMsgs, idBasedLocationHierarchy);
            for (Row row : rows) {
                if (skipRow(row, hierarchicalLocationTypeNames)) {
                    continue;
                }
                validateRow(row, hierarchicalLocationTypeNames, allErrorMsgs);
                createLocation(row, allErrorMsgs, hierarchicalLocationTypeNames);
            }
        } catch (ValidationException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw e;
        }
    }

    private boolean skipRow(Row row, List<String> hierarchicalLocationTypeNames) {
        List<String> values = row.get(hierarchicalLocationTypeNames);
        return CollectionUtil.anyStartsWith(values, ImportLocationsConstants.EXAMPLE);
    }
}
