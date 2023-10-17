package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
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
    private LocationRepository locationRepository = mock(LocationRepository.class);


    @Before
    public void setup() {
        parent = new AddressLevelType();
        parent.setName("Block");
        child = new AddressLevelType();
        child.setName("GP");
        child.setParent(parent);

        initMocks(this);
    }

    @Test
    public void shouldReturnSingleLocationIfOnlyOneFound() throws Exception {
        Row row = new Row(new String[]{"GP"}, new String[]{"gp1"});
        AddressLevel gp1AddressLevel = new AddressLevelBuilder().title("gp1").type(child).build();

        when(locationRepository.findByTitleAndType(eq("gp1"), eq(child), any())).thenReturn(Collections.singletonList(gp1AddressLevel));

        AddressLevel addressLevel = new AddressLevelCreator(locationRepository).findAddressLevel(row, asList(parent, child));
        assertThat(addressLevel).isEqualTo(gp1AddressLevel);

        verify(locationRepository).findByTitleAndType(eq("gp1"), eq(child), any());
    }


    @Test(expected = Exception.class)
    public void shouldThrowExceptionIfNoAddressFound() throws Exception {
        Row row = new Row(new String[]{"GP"}, new String[]{"non-existentAddress"});
        AddressLevel gp1AddressLevel = new AddressLevelBuilder().title("gp1").type(child).build();

        when(locationRepository.findByTitleAndType(eq("gp1"), eq(child), any())).thenReturn(Collections.singletonList(gp1AddressLevel));
        new AddressLevelCreator(locationRepository).findAddressLevel(row, asList(parent, child));
    }

    @Test
    public void shouldFetchFromLineageIfMoreThanOneAddressFound() throws Exception {
        Row row = new Row(new String[]{"Block", "GP"}, new String[]{"aParent", "child"});
        AddressLevel aParent = new AddressLevelBuilder().title("aParent").type(child).build();
        AddressLevel anotherParent = new AddressLevelBuilder().title("anotherParent").type(child).build();
        AddressLevel aChild = new AddressLevelBuilder().title("child").type(child).parent(aParent).build();
        AddressLevel anotherChild = new AddressLevelBuilder().title("child").type(child).parent(anotherParent).build();

        when(locationRepository.findByTitleAndType(eq("child"), eq(child), any())).thenReturn(asList(aChild, anotherChild));
        when(locationRepository.findByTitleLineageIgnoreCase("aParent, child")).thenReturn(Optional.of(aChild));

        AddressLevel addressLevel = new AddressLevelCreator(locationRepository).findAddressLevel(row, asList(parent, child));
        assertThat(addressLevel).isEqualTo(aChild);

        verify(locationRepository).findByTitleAndType(eq("child"), eq(child), any());
        verify(locationRepository).findByTitleLineageIgnoreCase("aParent, child");
    }
}
