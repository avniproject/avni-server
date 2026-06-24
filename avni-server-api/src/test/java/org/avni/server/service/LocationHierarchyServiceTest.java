package org.avni.server.service;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class LocationHierarchyServiceTest {

    private static final String STATE_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String DISTRICT_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String STATE_DISTRICT_LINEAGE = STATE_UUID + "." + DISTRICT_UUID;

    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;

    private LocationHierarchyService locationHierarchyService;
    private AddressLevelType state;
    private AddressLevelType district;

    @Before
    public void setUp() {
        initMocks(this);
        locationHierarchyService = new LocationHierarchyService(organisationConfigService, addressLevelTypeRepository, locationRepository, subjectTypeRepository);

        state = new AddressLevelTypeBuilder().withId(1L).withUuid(STATE_UUID).name("State").level(2.0).build();
        district = new AddressLevelTypeBuilder().withId(2L).withUuid(DISTRICT_UUID).name("District").level(1.0).parent(state).build();

        Organisation organisation = new TestOrganisationBuilder().setId(1L).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);
    }

    @Test
    public void buildHierarchyEmitsUuidsNotIds() {
        TreeSet<String> hierarchies = locationHierarchyService.buildHierarchyForAddressLevelTypes(List.of(district));
        assertEquals(Set.of(STATE_DISTRICT_LINEAGE), hierarchies);
    }

    @Test
    public void readResolvesUuidLineagesToLocalIds() {
        stubConfigWith(new ArrayList<>(List.of(STATE_DISTRICT_LINEAGE)));
        when(addressLevelTypeRepository.findByUuidIn(any())).thenReturn(List.of(state, district));

        List<Long> ids = locationHierarchyService.getLowestAddressLevelTypeHierarchiesForOrganisation();

        assertEquals(List.of(1L, 2L), ids);
    }

    @Test
    public void readFallsBackToLegacyIdSegments() {
        stubConfigWith(new ArrayList<>(List.of("999", STATE_DISTRICT_LINEAGE)));
        when(addressLevelTypeRepository.findByUuidIn(any())).thenReturn(List.of(state, district));

        List<Long> ids = locationHierarchyService.getLowestAddressLevelTypeHierarchiesForOrganisation();

        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of(999L, 1L, 2L)));
    }

    @Test
    public void writeGuardDropsLegacyIdLineagesFromTheMergedUnion() {
        JsonObject settings = new JsonObject().with("lowestAddressLevelType", new ArrayList<>(List.of("111.222")));
        when(addressLevelTypeRepository.findByUuidIn(any())).thenReturn(List.of(district));

        TreeSet<String> toSave = locationHierarchyService.determineAddressHierarchiesToBeSaved(settings, new HashSet<>(List.of(DISTRICT_UUID)));

        assertEquals(Set.of(STATE_DISTRICT_LINEAGE), toSave);
    }

    private void stubConfigWith(ArrayList<String> lineages) {
        OrganisationConfig organisationConfig = new OrganisationConfig();
        organisationConfig.setSettings(new JsonObject().with("lowestAddressLevelType", lineages));
        when(organisationConfigService.getOrganisationConfig(any(Organisation.class))).thenReturn(organisationConfig);
    }
}
