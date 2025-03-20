package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class SubjectHeadersCreatorIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private SubjectHeadersCreator subjectHeadersCreator;

    @Autowired
    private FormMappingRepository formMappingRepository;

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;

    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;

    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    private TestDataSetupService testDataSetupService;

    private FormMapping formMapping;
    private SubjectType subjectType;
    private long organisationId;

    @Before
    public void setUp() throws Exception{
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());
        organisationId = organisationData.getOrganisationId();

        subjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setName("TestSubject")
                .setType(Subject.Individual)
                .build();
        testSubjectTypeService.createWithDefaults(subjectType);

        formMapping = formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(subjectType, FormType.IndividualProfile).getFirst();

        AddressLevelType district = new AddressLevelTypeBuilder()
                .name("District")
                .level(2.0)
                .withUuid(UUID.randomUUID().toString())
                .build();
        addressLevelTypeRepository.save(district);

        AddressLevelType village = new AddressLevelTypeBuilder()
                .name("Village")
                .level(1.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(district)
                .build();
        addressLevelTypeRepository.save(village);

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
        assertTrue(containsHeader(headers, "Village"), "Should include the address level type");
        assertTrue(containsHeader(headers, "District"), "Should include the address level type");
    }

    @Test
    public void testPersonSubjectTypeHeaders() {
        SubjectType personType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setName("TestPerson")
                .setType(Subject.Person)
                .build();
        testSubjectTypeService.createWithDefaults(personType);

        FormMapping personMapping = formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(personType, FormType.IndividualProfile).getFirst();

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
                .setMandatoryFieldsForNewEntity()
                .setName("Household")
                .setHousehold(true)
                .setType(Subject.Household)
                .build();
        testSubjectTypeService.createWithDefaults(householdType);

        FormMapping householdMapping = formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(householdType, FormType.IndividualProfile).getFirst();

        String[] headers = subjectHeadersCreator.getAllHeaders(householdMapping);;

        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.profilePicture));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.totalMembers));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.lastName));
        assertFalse(containsHeader(headers, SubjectHeadersCreator.gender));
    }

    @Test
    public void testMultipleSubjectTypesForForm() {
        SubjectType anotherSubjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setName("AnotherSubject")
                .setType(Subject.Individual)
                .build();
        testSubjectTypeService.createWithDefaults(anotherSubjectType);

        FormMapping anotherMapping = formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(anotherSubjectType, FormType.IndividualProfile).getFirst();
        anotherMapping.setForm(formMapping.getForm());

        formMappingRepository.save(anotherMapping);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);

        assertTrue(containsHeader(headers, SubjectHeadersCreator.subjectTypeHeader));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtVillage() {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisationId);
        config.setSettings(new JsonObject(Map.of(
                "customRegistrationLocations", Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid(),
                                "locationTypeUUIDs", List.of(
                                        addressLevelTypeRepository.findByName("Village").getUuid()
                                )
                        )
                )
        )));
        organisationConfigRepository.save(config);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);

        assertTrue(containsHeader(headers, "Village"), "Should include Village as custom location");
        assertTrue(containsHeader(headers, "District"), "Should include District as it’s parent of Village");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtDistrict() {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisationId);
        config.setSettings(new JsonObject(Map.of(
                "customRegistrationLocations", Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid(),
                                "locationTypeUUIDs", List.of(
                                        addressLevelTypeRepository.findByName("District").getUuid()
                                )
                        )
                )
        )));
        organisationConfigRepository.save(config);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);

        assertTrue(containsHeader(headers, "District"), "Should include District as custom location");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village as it’s child of District");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsForDifferentSubjectType() {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisationId);
        config.setSettings(new JsonObject(Map.of(
                "customRegistrationLocations", Collections.singletonList(
                        Map.of(
                                "subjectTypeUUID", subjectType.getUuid() + "x",
                                "locationTypeUUIDs", List.of(
                                        addressLevelTypeRepository.findByName("District").getUuid()
                                )
                        )
                )
        )));
        organisationConfigRepository.save(config);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);

        assertTrue(containsHeader(headers, "Village"), "Should include default address level types subjectTypeUUID doesn’t match");
        assertTrue(containsHeader(headers, "District"), "Should include default address level types subjectTypeUUID doesn’t match");
    }

    @Test
    public void testAddressFieldsWithoutCustomRegistrationLocations() {
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping);

        assertTrue(containsHeader(headers, "Village"), "Should include address level types");
        assertTrue(containsHeader(headers, "District"), "Should include address level types");
    }

    @Test
    public void testDescriptionsGeneration() {
        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping);

        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping).length, descriptions.length,
                "Descriptions array should match headers array length");
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