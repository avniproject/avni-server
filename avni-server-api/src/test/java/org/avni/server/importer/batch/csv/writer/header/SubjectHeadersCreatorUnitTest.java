package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.*;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.AddressLevelService;
import org.avni.server.service.LocationHierarchyService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SubjectHeadersCreatorUnitTest {
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private AddressLevelService addressLevelService;

    @Mock
    private FormMappingRepository formMappingRepository;
    
    @Mock
    private LocationHierarchyService locationHierarchyService;

    private SubjectHeadersCreator subjectHeadersCreator;
    private AddressLevelType state;
    private AddressLevelType district;
    private AddressLevelType block;
    private AddressLevelType panchayat;
    private AddressLevelType village;

    @Before
    public void setUp() {
        initMocks(this);

        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        state = new AddressLevelTypeBuilder().withId(1L).name("State").level(5.0).build();
        district = new AddressLevelTypeBuilder().withId(2L).name("District").level(4.0).parent(state).build();
        block = new AddressLevelTypeBuilder().withId(3L).name("Block").level(3.0).parent(district).build();
        panchayat = new AddressLevelTypeBuilder().withId(4L).name("Panchayat").level(2.0).parent(block).build();
        village = new AddressLevelTypeBuilder().withId(5L).name("Village").level(1.0).parent(panchayat).build();

        when(addressLevelTypeRepository.findAllByIdIn(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(List.of(state, district, block, panchayat, village));
        when(addressLevelTypeRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(state, district));

        subjectHeadersCreator = new SubjectHeadersCreator(
                addressLevelTypeRepository,
                addressLevelService
        );
    }

    private FormMapping createFormMapping(SubjectType subjectType) {
          Form form = new TestFormBuilder()
                  .withFormType(FormType.IndividualProfile)
                  .build();

        return new FormMappingBuilder()
                .withSubjectType(subjectType)
                .withForm(form)
                .build();
    }

    @Test
    public void testBasicHeaderGeneration() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1L)
                .setName("TestSubject")
                .setType(Subject.Individual)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        Map<String, String> mockHierarchy = Map.of("1.2.3.4.5", "State -> District -> Block -> Panchayat -> Village");
        when(locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType)).thenReturn(mockHierarchy);
        
        String locationHierarchy = mockHierarchy.keySet().iterator().next();
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.id));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationDate));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationCoordinates));
        assertTrue(containsHeader(headers, "Village"), "Should include Village");
        assertTrue(containsHeader(headers, "Panchayat"), "Should include Panchayat");
        assertTrue(containsHeader(headers, "Block"), "Should include Block");
        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");

        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping, locationHierarchy);
        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy).length, descriptions.length);
    }

    @Test
    public void testPersonSubjectTypeHeaders() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1L)
                .setName("TestPerson")
                .setType(Subject.Person)
                .setAllowProfilePicture(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        Map<String, String> mockHierarchy = Map.of("1.2.3.4.5", "State -> District -> Block -> Panchayat -> Village");
        when(locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType)).thenReturn(mockHierarchy);
        
        String locationHierarchy = mockHierarchy.keySet().iterator().next();
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.dateOfBirth));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.dobVerified));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.gender));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
    }

    @Test
    public void testHouseholdSubjectTypeHeaders() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1L)
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        Map<String, String> mockHierarchy = Map.of("1.2.3.4.5", "State -> District -> Block -> Panchayat -> Village");
        when(locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType)).thenReturn(mockHierarchy);
        
        String locationHierarchy = mockHierarchy.keySet().iterator().next();
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.totalMembers));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.gender));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtDistrict() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1L)
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(district);

        Map<String, String> mockHierarchy = Map.of("1.2", "State -> District");
        when(locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType)).thenReturn(mockHierarchy);
        
        String locationHierarchy = mockHierarchy.keySet().iterator().next();
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);
        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");
        assertFalse(containsHeader(headers, "Block"), "Should exclude Block");
        assertFalse(containsHeader(headers, "Panchayat"), "Should exclude Panchayat");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village");
    }


    private boolean containsHeader(String[] headers, String headerToFind) {
        for (String header : headers) {
            if (header.equals(headerToFind)) {
                return true;
            }
        }
        return false;
    }
}
