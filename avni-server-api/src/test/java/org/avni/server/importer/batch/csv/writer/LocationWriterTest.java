package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.FormService;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class LocationWriterTest {
    private AddressLevelTypeRepository addressLevelTypeRepository;
    private LocationRepository locationRepository;
    private LocationService locationService;
    private ImportService importService;
    private LocationWriter locationWriter;

    @Before
    public void setup() {
        addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
        locationRepository = mock(LocationRepository.class);
        locationService = mock(LocationService.class);
        importService = mock(ImportService.class);
        locationWriter = new LocationWriter(locationService, locationRepository, addressLevelTypeRepository, mock(ObservationCreator.class), importService, mock(FormService.class));
        when(locationService.save(any())).thenReturn(new AddressLevel());
    }

    @Test(expected = Exception.class)
    public void shouldDisallowUnknownHeadersInCreateFile() throws Exception {
        createModeFileSetup();

        ArrayList<Row> rows = new ArrayList<>();
        String[] headers = {blockType().getName(), hierarchyOneOfAddressLevelTypes().get(1).getName(), "Extra Header"};
        AddressLevel b1 = new AddressLevelBuilder().id(1).type(blockType()).title("B1").build();
        AddressLevel v1 = new AddressLevelBuilder().id(2).type(hierarchyOneOfAddressLevelTypes().get(1)).title("V1").build();

        rows.add(new Row(headers, new String[]{b1.getTitle(), v1.getTitle(), "new address level"}));

        locationWriter.write(rows);
    }

    @Test(expected = Exception.class)
    public void shouldIncludeHierarchyInOrder() throws Exception {
        createModeFileSetup();
        ArrayList<Row> rows = new ArrayList<>();
        String[] headers = {hierarchyOneOfAddressLevelTypes().get(1).getName(), blockType().getName()}; //headers not in order of hierarchy
        AddressLevel b1 = new AddressLevelBuilder().id(1).type(blockType()).title("B1").build();
        AddressLevel v1 = new AddressLevelBuilder().id(2).type(hierarchyOneOfAddressLevelTypes().get(1)).title("V1").build();

        rows.add(new Row(headers, new String[]{b1.getTitle(), v1.getTitle()}));

        locationWriter.write(rows);
    }

    private void createModeFileSetup() throws Exception {
        String dummyHierarchy = "1.2";
        locationWriter.setLocationUploadMode(String.valueOf(LocationWriter.LocationUploadMode.CREATE));
        locationWriter.setLocationHierarchy(dummyHierarchy);

        when(importService.getAddressLevelTypesForCreateModeSingleHierarchy(dummyHierarchy)).thenReturn(hierarchyOneOfAddressLevelTypes());
    }

    private List<AddressLevelType> hierarchyOneOfAddressLevelTypes() {
        return Arrays.asList(blockType(),
                new AddressLevelTypeBuilder().name("Village").level(2.0).build());
    }

    private AddressLevelType blockType() {
        return new AddressLevelTypeBuilder().name("Block").level(3.0).build();
    }

    private List<AddressLevelType> hierarchyTwoOfAddressLevelTypes() {
        return Arrays.asList(subcenter(),
                new AddressLevelTypeBuilder().name("AWC").level(2.0).build());
    }

    private AddressLevelType subcenter() {
        return new AddressLevelTypeBuilder().name("Sub Center").level(3.0).build();
    }
}
