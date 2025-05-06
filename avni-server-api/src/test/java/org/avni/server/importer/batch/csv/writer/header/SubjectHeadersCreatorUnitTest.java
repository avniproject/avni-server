package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.*;
import org.avni.server.common.AbstractControllerIntegrationTest;
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
import org.avni.server.service.ImportService;
import org.avni.server.service.OrganisationConfigService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SubjectHeadersCreatorUnitTest {
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private AddressLevelService addressLevelService;

    @Mock
    private FormMappingRepository formMappingRepository;

    private SubjectHeadersCreator subjectHeadersCreator;
    private AddressLevelType village;
    private AddressLevelType district;

    @Before
    public void setUp() {
        initMocks(this);

        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        district = new AddressLevelTypeBuilder().name("District").build();
        village = new AddressLevelTypeBuilder().withUuid(UUID.randomUUID()).name("Village").parent(district).build();
        when(addressLevelTypeRepository.getAllParentNames(village.getUuid())).thenReturn(List.of(village.getName(), district.getName()));

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
                .setId(1l)
                .setName("TestSubject")
                .setType(Subject.Individual)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.id));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationDate));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationLocation));
        assertTrue(containsHeader(headers, "Village"), "Should include default address level types");
        assertTrue(containsHeader(headers, "District"), "Should include default address level types");

        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping, null);
        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping, null).length, descriptions.length);
    }

    @Test
    public void testPersonSubjectTypeHeaders() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1l)
                .setName("TestPerson")
                .setType(Subject.Person)
                .setAllowProfilePicture(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);
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
                .setId(1l)
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(village);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.totalMembers));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.gender));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtDistrict() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1l)
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(district);
        when(addressLevelTypeRepository.getAllParentNames(district.getUuid())).thenReturn(List.of(district.getName()));

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);
        assertTrue(containsHeader(headers, "District"), "Should include District as custom location");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village as it’s child of District");
    }

    @Test(expected = InvalidConfigurationException.class)
    public void testAddressFieldsWithoutCustomRegistrationLocations() throws InvalidConfigurationException {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setId(1l)
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping formMapping = createFormMapping(subjectType);

        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));
        when(addressLevelService.getRegistrationLocationType(subjectType)).thenReturn(null);
        subjectHeadersCreator.getAllHeaders(formMapping, null);
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
