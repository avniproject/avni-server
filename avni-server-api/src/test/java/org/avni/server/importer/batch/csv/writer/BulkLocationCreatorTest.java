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

public class BulkLocationCreatorTest {
    private ImportService importService;
    private BulkLocationCreator bulkLocationCreator;
    private String dummyHierarchy;

    @Before
    public void setup() {
        AddressLevelTypeRepository addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        LocationService locationService = mock(LocationService.class);
        importService = mock(ImportService.class);
        bulkLocationCreator = new BulkLocationCreator(locationService, locationRepository, addressLevelTypeRepository, mock(ObservationCreator.class), importService, mock(FormService.class), null);
        when(locationService.save(any())).thenReturn(new AddressLevel());
        dummyHierarchy = "1.2";
    }

    @Test(expected = Exception.class)
    public void shouldDisallowUnknownHeadersInCreateFile() throws Exception {
        createModeFileSetup();

        ArrayList<Row> rows = new ArrayList<>();
        String[] headers = {blockType().getName(), hierarchyOneOfAddressLevelTypes().get(1).getName(), "Extra Header"};
        AddressLevel b1 = new AddressLevelBuilder().id(1).type(blockType()).title("B1").build();
        AddressLevel v1 = new AddressLevelBuilder().id(2).type(hierarchyOneOfAddressLevelTypes().get(1)).title("V1").build();

        rows.add(new Row(headers, new String[]{b1.getTitle(), v1.getTitle(), "new address level"}));

        bulkLocationCreator.write(rows, dummyHierarchy);
    }

    @Test(expected = Exception.class)
    public void shouldIncludeHierarchyInOrder() throws Exception {
        createModeFileSetup();
        ArrayList<Row> rows = new ArrayList<>();
        String[] headers = {hierarchyOneOfAddressLevelTypes().get(1).getName(), blockType().getName()}; //headers not in order of hierarchy
        AddressLevel b1 = new AddressLevelBuilder().id(1).type(blockType()).title("B1").build();
        AddressLevel v1 = new AddressLevelBuilder().id(2).type(hierarchyOneOfAddressLevelTypes().get(1)).title("V1").build();

        rows.add(new Row(headers, new String[]{b1.getTitle(), v1.getTitle()}));

        bulkLocationCreator.write(rows, dummyHierarchy);
    }

    private void createModeFileSetup() {
        when(importService.getAddressLevelTypesForCreateModeSingleHierarchy(dummyHierarchy)).thenReturn(hierarchyOneOfAddressLevelTypes());
    }

    private List<AddressLevelType> hierarchyOneOfAddressLevelTypes() {
        return Arrays.asList(blockType(),
                new AddressLevelTypeBuilder().name("Village").level(2.0).build());
    }

    private AddressLevelType blockType() {
        return new AddressLevelTypeBuilder().name("Block").level(3.0).build();
    }
}
