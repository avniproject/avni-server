package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.*;

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
    private OrganisationConfigService organisationConfigService;

    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    private TestDataSetupService testDataSetupService;

    @Autowired
    private TestFormService testFormService;

    @Autowired
    private TestConceptService testConceptService;

    private FormMapping formMapping;
    private SubjectType subjectType;

    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    private AddressLevelType district;
    private AddressLevelType village;

    @Before
    public void setUp() throws Exception{
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());

        subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName("SubjectType1").build());

        Concept multiSelectDecisionCoded = testConceptService.createCodedConcept("Multi Select Decision Coded", "MSDC Answer 1", "MSDC Answer 2", "MSDC Answer 3", "MSDC Answer 4");

        Concept questionGroupConcept = testConceptService.createConcept("QuestionGroup Concept", ConceptDataType.QuestionGroup);
        List<Concept> childQGConcepts = new ArrayList<>();
        childQGConcepts.add(testConceptService.createConcept("QG Numeric Concept", ConceptDataType.Numeric));

        Concept repeatableQuestionGroupConcept = testConceptService.createConcept("Repeatable QuestionGroup Concept", ConceptDataType.QuestionGroup);
        List<Concept> childRQGConcepts = new ArrayList<>();
        childRQGConcepts.add(testConceptService.createConcept("RQG Numeric Concept", ConceptDataType.Numeric));
        formMapping = testFormService.createRegistrationForm(subjectType, "Registration Form",
                new ArrayList<>(),
                new ArrayList<>(),
                questionGroupConcept,
                childQGConcepts,
                repeatableQuestionGroupConcept,
                childRQGConcepts);
        testFormService.addDecisionConcepts(formMapping.getForm().getId(), multiSelectDecisionCoded);

        district = new AddressLevelTypeBuilder()
                .name("District")
                .level(2.0)
                .withUuid(UUID.randomUUID().toString())
                .build();
        addressLevelTypeRepository.save(district);

        village = new AddressLevelTypeBuilder()
                .name("Village")
                .level(1.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(district)
                .build();
        addressLevelTypeRepository.save(village);
    }

    @Test
    public void testBasicHeaderGeneration() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);

        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.id));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationDate));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationLocation));
        assertTrue(containsHeader(headers, "Village"), "Should include the address level type");
        assertTrue(containsHeader(headers, "District"), "Should include the address level type");
        assertTrue(containsHeader(headers, "\"Multi Select Decision Coded\""));
        assertTrue(containsHeader(headers,"\"QuestionGroup Concept|QG Numeric Concept\""));
        assertTrue(containsHeader(headers,"\"Repeatable QuestionGroup Concept|RQG Numeric Concept|1\""));
    }

    @Test
    public void testMultipleSubjectTypesForForm() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        SubjectType anotherSubjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setName("AnotherSubject")
                .setType(Subject.Individual)
                .build();
        testSubjectTypeService.createWithDefaults(anotherSubjectType);

        FormMapping anotherMapping = formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(anotherSubjectType, FormType.IndividualProfile).getFirst();
        anotherMapping.setForm(formMapping.getForm());

        formMappingRepository.save(anotherMapping);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);

        assertTrue(containsHeader(headers, SubjectHeadersCreator.subjectTypeHeader));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtVillage() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);

        assertTrue(containsHeader(headers, "Village"), "Should include Village as custom location");
        assertTrue(containsHeader(headers, "District"), "Should include District as it’s parent of Village");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtNotTheLowestLevel() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(district, subjectType);

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);

        assertTrue(containsHeader(headers, "District"), "Should include District as custom location");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village as it’s child of District");
    }

    @Test
    public void testAddressFieldsWithoutCustomRegistrationLocations() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, null);

        assertTrue(containsHeader(headers, "Village"), "Should include address level types");
        assertTrue(containsHeader(headers, "District"), "Should include address level types");
    }

    @Test
    public void testDescriptionsGeneration() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping, null);

        assertEquals("Optional. Can be used to later identify the entry.", descriptions[0]);
        assertEquals("Mandatory. SubjectType1.", descriptions[1]);
        assertEquals("Mandatory. Format: DD-MM-YYYY or YYYY-MM-DD.", descriptions[2]);
        assertEquals("\"Optional. Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172).\"", descriptions[3]);
        assertEquals("Mandatory", descriptions[4]);
        assertEquals("Mandatory", descriptions[5]);
        assertEquals("Optional", descriptions[6]);
        assertEquals("Mandatory. Format: DD-MM-YYYY or YYYY-MM-DD.", descriptions[7]);
        assertEquals("\"Optional. Default value: false. Allowed values: {true, false}.\"", descriptions[8]);
        assertEquals("\"Mandatory. Allowed values: {Female, Male, Other}.\"", descriptions[9]);
        assertEquals("Mandatory", descriptions[10]);
        assertEquals("Mandatory", descriptions[11]);
        assertEquals("Optional. Allowed values: Any number.", descriptions[12]);
        assertEquals("Optional. Allowed values: Any number.", descriptions[13]);
        assertEquals("\"Optional. Allowed values: {MSDC Answer 1, MSDC Answer 2, MSDC Answer 3, MSDC Answer 4} Format: May allow single value or multiple values separated a comma. Please check with developer..\"", descriptions[14]);

        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping, null).length, descriptions.length,
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
