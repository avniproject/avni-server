package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.service.FormService;
import org.avni.server.service.ImportService;
import org.avni.server.service.LocationService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class LocationWriterTest {
    private AddressLevelTypeRepository addressLevelTypeRepository;
    private LocationRepository locationRepository;
    private LocationService locationService;
    private LocationWriter locationWriter;

    @Before
    public void setup() {
        addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
        locationRepository = mock(LocationRepository.class);
        locationService = mock(LocationService.class);

        locationWriter = new LocationWriter(locationService, locationRepository, addressLevelTypeRepository, mock(ObservationCreator.class), mock(ImportService.class), mock(FormService.class));
        when(locationService.save(any())).thenReturn(new AddressLevel());
    }

    @Test
    public void process_Same_Name_New_Child_Location_Across_Different_Hierarchy__Strict() throws Exception {
        //TODO rewrite this test
//        List<AddressLevelType> addressLevelTypesOne = hierarchyOneOfAddressLevelTypes();
//        List<AddressLevelType> addressLevelTypesTwo = hierarchyTwoOfAddressLevelTypes();
//        List<AddressLevelType> allAddressLevelTypes = new ArrayList<>();
//        allAddressLevelTypes.addAll(addressLevelTypesOne);
//        allAddressLevelTypes.addAll(addressLevelTypesTwo);
//
//        String villageNameWhichIsAlsoAWCName = "VillageNameWhichIsAlsoAWCName";
//
//        AddressLevel sc1 = new AddressLevelBuilder().id(1).type(subcenter()).title("SC1").build();
//
//        when(addressLevelTypeRepository.findAllByIsVoidedFalse()).thenReturn(allAddressLevelTypes);
//        when(locationRepository.findLocation(sc1.getTitle(), subcenter().getName())).thenReturn(sc1);
//        when(locationRepository.findChildLocation(sc1.getTitle(), subcenter().getName(), null)).thenReturn(sc1);
//
//        when(locationRepository.findLocation(villageNameWhichIsAlsoAWCName, "AWC")).thenReturn(null);
//        when(locationRepository.findChildLocation(sc1, villageNameWhichIsAlsoAWCName)).thenReturn(null);
//
//        locationWriter.setLocationUploadMode("strict");
//        locationWriter.init();
//
//        ArrayList<Row> rows = new ArrayList<>();
//        String[] headers = {subcenter().getName(), "AWC"};
//
//        rows.add(new Row(headers, new String[]{sc1.getTitle(), villageNameWhichIsAlsoAWCName}));
//
//        locationWriter.write(rows);
//
//        ArgumentCaptor<LocationContract> argument = ArgumentCaptor.forClass(LocationContract.class);
//        verify(locationService).save(argument.capture());
//        LocationContract savedLocation = argument.getValue();
//        assertEquals("Awc", savedLocation.getType());
//        assertEquals(villageNameWhichIsAlsoAWCName, savedLocation.getName());
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
