package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.*;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Account;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ImportService;
import org.avni.server.service.OrganisationConfigService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class SubjectHeadersCreatorUnitTest extends AbstractControllerIntegrationTest {
    @Mock
    private ImportService importService;

    @Mock
    private OrganisationConfigService organisationConfigService;

    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;

    @Mock
    private FormMappingRepository formMappingRepository;

    private SubjectHeadersCreator subjectHeadersCreator;
    private FormMapping formMapping;
    private SubjectType subjectType;

    @Before
    public void setUp() {
        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(Collections.emptyList());
        when(addressLevelTypeRepository.getAllNames()).thenReturn(List.of("Village", "District"));

        subjectType = new SubjectTypeBuilder()
                .setName("TestSubject")
                .setType(Subject.Individual)
                .setUuid(UUID.randomUUID().toString())
                .build();

        formMapping = createFormMapping(subjectType);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile))
                .thenReturn(Collections.singletonList(formMapping));

        List<FieldDescriptor> strategyList = Arrays.asList(
                new CodedFieldDescriptor(),
                new DateFieldDescriptor(),
                new TextFieldDescriptor(),
                new NumericFieldDescriptor()
        );

        subjectHeadersCreator = new SubjectHeadersCreator(
                organisationConfigService,
                addressLevelTypeRepository
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
    public void testBasicHeaderGeneration() {
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.id));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationDate));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationLocation));
        assertTrue(containsHeader(headers, "Village"), "Should include default address level types");
        assertTrue(containsHeader(headers, "District"), "Should include default address level types");
    }

    @Test
    public void testPersonSubjectTypeHeaders() {
        SubjectType personType = new SubjectTypeBuilder()
                .setName("TestPerson")
                .setType(Subject.Person)
                .setAllowProfilePicture(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping personMapping = createFormMapping(personType);

        String[] headers = subjectHeadersCreator.getAllHeaders(personMapping);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.dateOfBirth));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.dobVerified));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.gender));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
    }

    @Test
    public void testHouseholdSubjectTypeHeaders() {
        SubjectType householdType = new SubjectTypeBuilder()
                .setName("Household")
                .setType(Subject.Household)
                .setHousehold(true)
                .setUuid(UUID.randomUUID().toString())
                .build();
        FormMapping householdMapping = createFormMapping(householdType);

        String[] headers = subjectHeadersCreator.getAllHeaders(householdMapping);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.totalMembers));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.gender));
    }

    @Test
    public void testMultipleSubjectTypesForForm() {
        SubjectType anotherSubjectType = new SubjectTypeBuilder()
                .setName("AnotherSubject")
                .setType(Subject.Individual)
                .setUuid(UUID.randomUUID().toString())
                .build();

        FormMapping anotherMapping = createFormMapping(anotherSubjectType);
        anotherMapping.setForm(formMapping.getForm());
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(anotherSubjectType, FormType.IndividualProfile))
                .thenReturn(List.of(formMapping,anotherMapping));
        when(formMappingRepository.getSubjectTypesMappedToAForm(formMapping.getFormUuid())).thenReturn(List.of(subjectType,anotherSubjectType));
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.subjectTypeHeader));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtVillage() {
        String villageUuid = UUID.randomUUID().toString();
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(
                Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid(),
                                "locationTypeUUIDs", List.of(villageUuid)
                        )
                )
        );
        when(addressLevelTypeRepository.getAllParentNames(villageUuid)).thenReturn(List.of("Village", "District"));

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertTrue(containsHeader(headers, "Village"), "Should include Village as custom location");
        assertTrue(containsHeader(headers, "District"), "Should include District as it’s parent of Village");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtDistrict() {
        String districtUuid = UUID.randomUUID().toString();
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(
                Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid(),
                                "locationTypeUUIDs", List.of(districtUuid)
                        )
                )
        );
        when(addressLevelTypeRepository.getAllParentNames(districtUuid)).thenReturn(List.of("District"));

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertTrue(containsHeader(headers, "District"), "Should include District as custom location");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village as it’s child of District");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsForDifferentSubjectType() {
        String districtUuid = UUID.randomUUID().toString();
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(
                Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid() + "x",
                                "locationTypeUUIDs", List.of(districtUuid)
                        )
                )
        );
        when(addressLevelTypeRepository.getAllParentNames(districtUuid)).thenReturn(List.of("District"));

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertTrue(containsHeader(headers, "Village"), "Should include default address level types subjectTypeUUID doesn’t match");
        assertTrue(containsHeader(headers, "District"), "Should include default address level types subjectTypeUUID doesn’t match");
    }

    @Test
    public void testAddressFieldsWithoutCustomRegistrationLocations() {
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(Collections.emptyList());
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);
        assertTrue(containsHeader(headers, "Village"), "Should include address level types");
        assertTrue(containsHeader(headers, "District"), "Should include address level types");
    }

    @Test
    public void testDescriptionsGeneration() {
        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping);
        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping).length, descriptions.length);
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
