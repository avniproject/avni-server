package org.avni.server.service;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationMappingRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.avni.server.web.request.webapp.search.LocationSearchRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class LocationServiceTest {

    @Mock
    private LocationMappingRepository locationMappingRepository;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ResetSyncService resetSyncService;
    @Mock
    private OrganisationConfigService organisationConfigService;

    private LocationService locationService;

    private Organisation organisation;

    @Before
    public void before() {
        initMocks(this);
        locationService = new LocationService(locationRepository, addressLevelTypeRepository, organisationRepository, locationMappingRepository, resetSyncService, organisationConfigService);

        organisation = new TestOrganisationBuilder().setId(1L).build();
        User user = new UserBuilder().id(10L).userName("tester").build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        userContext.setUser(user);
        UserContextHolder.create(userContext);
    }

    @Test
    public void shouldSearchByIdAloneIfAddressLevelAndParentNotAvailable() {
        String searchString = "mum";
        LocationSearchRequest searchRequest = new LocationSearchRequest(searchString, null, null, mock(Pageable.class));
        locationService.find(searchRequest);

        verify(locationRepository).findLocationProjectionByTitleIgnoreCase(eq(searchString), any(Pageable.class));
    }

    @Test
    public void shouldSearchByAddressLevelTypeIfAvailable() {
        String searchString = "mum";
        int addressLevelTypeId = 12;
        LocationSearchRequest searchRequest = new LocationSearchRequest(searchString, addressLevelTypeId, null, mock(Pageable.class));
        locationService.find(searchRequest);

        verify(locationRepository).findLocationProjectionByTitleIgnoreCaseAndTypeId(matches(searchString), eq(addressLevelTypeId), any(Pageable.class));
    }

    @Test
    public void shouldSearchByAddressLevelTypeAndParentIfAvailable() {
        String searchString = "mum";
        int addressLevelTypeId = 12;
        Integer parentId = 123;
        LocationSearchRequest searchRequest = new LocationSearchRequest(searchString, addressLevelTypeId, parentId, mock(Pageable.class));
        locationService.find(searchRequest);

        verify(locationRepository).findLocationProjectionByTitleIgnoreCaseAndTypeIdAndParentId(eq(searchString), eq(addressLevelTypeId), eq(parentId), any(Pageable.class));
    }

    @Test
    public void findByAncestorAndFiltersShouldPassNullLineagePrefixWhenAncestorIdIsNull() {
        String title = "village";
        Integer typeId = 7;
        Pageable pageable = mock(Pageable.class);

        locationService.findByAncestorAndFilters(title, typeId, null, pageable);

        verify(locationRepository).findLocationProjectionByAncestorAndFilters(eq(title), eq(typeId), eq(null), eq(pageable));
    }

    @Test
    public void findByAncestorAndFiltersShouldPassAncestorLineageAsPrefix() {
        String title = "village";
        Integer typeId = 7;
        Long ancestorId = 42L;
        Pageable pageable = mock(Pageable.class);

        AddressLevel ancestor = new AddressLevel();
        ancestor.setLineage("1.5.42");
        when(locationRepository.findById(ancestorId)).thenReturn(Optional.of(ancestor));

        locationService.findByAncestorAndFilters(title, typeId, ancestorId, pageable);

        verify(locationRepository).findLocationProjectionByAncestorAndFilters(eq(title), eq(typeId), eq("1.5.42"), eq(pageable));
    }

    @Test
    public void findByAncestorAndFiltersShouldThrowWhenAncestorIdIsUnknown() {
        Long ancestorId = 999L;
        when(locationRepository.findById(ancestorId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> locationService.findByAncestorAndFilters("title", null, ancestorId, mock(Pageable.class)));
    }

    @Test
    public void voidingAnAddressLevelTypeCascadesTheVoidToItsLocations() {
        String uuid = UUID.randomUUID().toString();
        AddressLevelType existing = new AddressLevelTypeBuilder().withId(5L).withUuid(uuid).name("Village").level(1.0).build();
        when(addressLevelTypeRepository.findByUuid(uuid)).thenReturn(existing);

        AddressLevelTypeContract contract = new AddressLevelTypeContract();
        contract.setUuid(uuid);
        contract.setName("Village");
        contract.setLevel(1.0);
        contract.setVoided(true);

        locationService.createAddressLevelType(organisation, contract);

        verify(locationRepository).voidLocationsByAddressLevelTypeId(5L, 10L);
    }

    @Test
    public void savingANonVoidedAddressLevelTypeDoesNotCascade() {
        String uuid = UUID.randomUUID().toString();
        AddressLevelType existing = new AddressLevelTypeBuilder().withId(5L).withUuid(uuid).name("Village").level(1.0).build();
        when(addressLevelTypeRepository.findByUuid(uuid)).thenReturn(existing);

        AddressLevelTypeContract contract = new AddressLevelTypeContract();
        contract.setUuid(uuid);
        contract.setName("Village");
        contract.setLevel(1.0);
        contract.setVoided(false);

        locationService.createAddressLevelType(organisation, contract);

        verify(locationRepository, never()).voidLocationsByAddressLevelTypeId(anyLong(), anyLong());
    }
}
