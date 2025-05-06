package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.importer.batch.model.Row;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AddressLevelCreatorTest {
    private AddressLevelType parent;
    private AddressLevelType child;
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final AddressLevelTypeRepository addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
    private AddressLevelCreator addressLevelCreator;

    @Before
    public void setup() {
        parent = new AddressLevelType();
        parent.setName("Block");
        parent.setLevel(2.0);
        child = new AddressLevelType();
        child.setName("GP");
        child.setLevel(1.0);
        child.setParent(parent);

        initMocks(this);

        addressLevelCreator = new AddressLevelCreator(locationRepository, addressLevelTypeRepository);
    }

    @Test
    public void shouldFindSingleLocationIfOnlyOneFound() throws Exception {
        Row row = new Row(new String[]{"GP"}, new String[]{"gp1"});
        AddressLevel gp1AddressLevel = new AddressLevelBuilder().title("gp1").type(child).build();

        when(locationRepository.findByTitleIgnoreCaseAndType(eq("gp1"), eq(child), any())).thenReturn(Collections.singletonList(gp1AddressLevel));

        AddressLevel addressLevel = addressLevelCreator.findAddressLevel(row, new AddressLevelTypes(child, parent));
        assertThat(addressLevel).isEqualTo(gp1AddressLevel);

        verify(locationRepository).findByTitleIgnoreCaseAndType(eq("gp1"), eq(child), any());
    }

    @Test
    public void ifNoAddressFound() {
        Row row = new Row(new String[]{"GP"}, new String[]{"non-existentAddress"});
        AddressLevel gp1AddressLevel = new AddressLevelBuilder().title("gp1").type(child).build();

        when(locationRepository.findByTitleIgnoreCaseAndType(eq("gp1"), eq(child), any())).thenReturn(Collections.singletonList(gp1AddressLevel));
        AddressLevel addressLevel = addressLevelCreator.findAddressLevel(row, new AddressLevelTypes(child, parent));
        assertThat(addressLevel).isNull();
    }

    @Test
    public void shouldFetchFromLineageIfMoreThanOneAddressFound() throws Exception {
        Row row = new Row(new String[]{"Block", "GP"}, new String[]{"aParent", "child"});
        AddressLevel aParent = new AddressLevelBuilder().title("aParent").type(child).build();
        AddressLevel anotherParent = new AddressLevelBuilder().title("anotherParent").type(child).build();
        AddressLevel aChild = new AddressLevelBuilder().title("child").type(child).parent(aParent).build();
        AddressLevel anotherChild = new AddressLevelBuilder().title("child").type(child).parent(anotherParent).build();

        when(locationRepository.findByTitleIgnoreCaseAndType(eq("child"), eq(child), any())).thenReturn(asList(aChild, anotherChild));
        when(locationRepository.findByTitleLineageIgnoreCase("aParent, child")).thenReturn(Optional.of(aChild));
        when(addressLevelTypeRepository.getAllAddressLevelTypes()).thenReturn(new AddressLevelTypes(child, parent));

        AddressLevel addressLevel = addressLevelCreator.findAddressLevel(row, addressLevelTypeRepository.getAllAddressLevelTypes());
        assertThat(addressLevel).isEqualTo(aChild);

        verify(locationRepository).findByTitleIgnoreCaseAndType(eq("child"), eq(child), any());
        verify(locationRepository).findByTitleLineageIgnoreCase("aParent, child");
    }

    @Test
    public void shouldFetchFromLineageIfMoreThanOneAddressFoundAndValueForLowestIsEmpty() throws Exception {
        Row row = new Row(new String[]{"Block", "GP"}, new String[]{"aParent", " "});
        AddressLevel aParent = new AddressLevelBuilder().title("aParent").type(parent).build();

        when(locationRepository.findByTitleIgnoreCaseAndType(eq("aParent"), eq(parent), any())).thenReturn(Collections.singletonList(aParent));
        when(addressLevelTypeRepository.getAllAddressLevelTypes()).thenReturn(new AddressLevelTypes(child, parent));

        AddressLevel addressLevel = addressLevelCreator.findAddressLevel(row, addressLevelTypeRepository.getAllAddressLevelTypes());
        assertThat(addressLevel).isEqualTo(aParent);
    }
}
