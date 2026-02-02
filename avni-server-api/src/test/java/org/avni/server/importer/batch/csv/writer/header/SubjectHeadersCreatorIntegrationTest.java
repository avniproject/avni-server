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
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
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
    
    @Autowired
    private LocationHierarchyService locationHierarchyService;
    
    private AddressLevelType state;
    private AddressLevelType district;
    private AddressLevelType block;
    private AddressLevelType panchayat;
    private AddressLevelType village;
    private AddressLevelType hsc;

    @Before
    public void setUp() throws Exception {
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

        testConceptService.createConcept("Concept \"With\" Quotes", ConceptDataType.Text);

        Concept repeatableQuestionGroupConcept = testConceptService.createConcept("Repeatable QuestionGroup Concept", ConceptDataType.QuestionGroup);
        List<Concept> childRQGConcepts = new ArrayList<>();
        childRQGConcepts.add(testConceptService.createConcept("RQG Numeric Concept", ConceptDataType.Numeric));
        List<String> singleSelectConceptNames = new ArrayList<>();
        singleSelectConceptNames.add("Concept \"With\" Quotes");
        formMapping = testFormService.createRegistrationForm(subjectType, "Registration Form",
                singleSelectConceptNames,
                new ArrayList<>(),
                questionGroupConcept,
                childQGConcepts,
                repeatableQuestionGroupConcept,
                childRQGConcepts);
        testFormService.addDecisionConcepts(formMapping.getForm().getId(), multiSelectDecisionCoded);

        state = new AddressLevelTypeBuilder()
                .name("State")
                .level(5.0)
                .withUuid(UUID.randomUUID().toString())
                .build();
        addressLevelTypeRepository.save(state);

        district = new AddressLevelTypeBuilder()
                .name("District")
                .level(4.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(state)
                .build();
        addressLevelTypeRepository.save(district);

        block = new AddressLevelTypeBuilder()
                .name("Block")
                .level(3.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(district)
                .build();
        addressLevelTypeRepository.save(block);

        panchayat = new AddressLevelTypeBuilder()
                .name("Panchayat")
                .level(2.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(block)
                .build();
        addressLevelTypeRepository.save(panchayat);

        village = new AddressLevelTypeBuilder()
                .name("Village")
                .level(1.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(panchayat)
                .build();
        addressLevelTypeRepository.save(village);

        hsc = new AddressLevelTypeBuilder()
                .name("HSC")
                .level(2.0)
                .withUuid(UUID.randomUUID().toString())
                .parent(block)
                .build();
        addressLevelTypeRepository.save(hsc);
    }

    @Test
    public void testBasicHeaderGeneration() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy for Village registration");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(containsHeader(headers, SubjectHeadersCreator.id));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationDate));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.firstName));
        assertTrue(containsHeader(headers, SubjectHeadersCreator.registrationLocation));
        assertTrue(containsHeader(headers, "Village"), "Should include Village");
        assertTrue(containsHeader(headers, "Panchayat"), "Should include Panchayat");
        assertTrue(containsHeader(headers, "Block"), "Should include Block");
        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");
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

        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertTrue(containsHeader(headers, SubjectHeadersCreator.subjectTypeHeader));
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtHSC() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(hsc, subjectType);
        
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertTrue(containsHeader(headers, "HSC"), "Should include HSC");
        assertTrue(containsHeader(headers, "Block"), "Should include Block");
        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village");
        assertFalse(containsHeader(headers, "Panchayat"), "Should exclude Panchayat");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtPanchayat() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(panchayat, subjectType);
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertTrue(containsHeader(headers, "Panchayat"), "Should include Panchayat");
        assertTrue(containsHeader(headers, "Block"), "Should include Block");
        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village");
        assertFalse(containsHeader(headers, "HSC"), "Should exclude HSC");
    }

    @Test
    public void testAddressFieldsWithCustomRegistrationLocationsAtNotTheLowestLevel() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(district, subjectType);

        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertTrue(containsHeader(headers, "District"), "Should include District");
        assertTrue(containsHeader(headers, "State"), "Should include State");
        assertFalse(containsHeader(headers, "Block"), "Should exclude Block");
        assertFalse(containsHeader(headers, "Village"), "Should exclude Village");
        assertFalse(containsHeader(headers, "HSC"), "Should exclude HSC");
        assertFalse(containsHeader(headers, "Panchayat"), "Should exclude Panchayat");
    }

    @Test
    public void testAllHierarchiesAvailableWhenNoCustomRegistrationLocation() throws InvalidConfigurationException {
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(2, availableHierarchies.size(), "Should have both Village and HSC hierarchies when no custom location is configured");
    }

    @Test
    public void testDescriptionsGeneration() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        assertEquals(1, availableHierarchies.size(), "Should have exactly one hierarchy");
        String locationHierarchy = availableHierarchies.keySet().iterator().next();
        
        String[] descriptions = subjectHeadersCreator.getAllDescriptions(formMapping, locationHierarchy);

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
        assertEquals("Mandatory", descriptions[12]);
        assertEquals("Mandatory", descriptions[13]);
        assertEquals("Mandatory", descriptions[14]);
        assertEquals("Optional. Any Text.", descriptions[15]);
        assertEquals("Optional. Allowed values: Any number.", descriptions[16]);
        assertEquals("Optional. Allowed values: Any number.", descriptions[17]);
        assertEquals("\"Optional. Allowed values: {MSDC Answer 1, MSDC Answer 2, MSDC Answer 3, MSDC Answer 4} Format: May allow single value or multiple values separated a comma. Please check with developer..\"", descriptions[18]);

        assertNotNull(descriptions);
        assertEquals(subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy).length, descriptions.length,
                "Descriptions array should match headers array length");
    }

    @Test
    public void testHeaderGenerationForConceptWithQuotesInName() throws InvalidConfigurationException {
        organisationConfigService.saveRegistrationLocation(village, subjectType);
        Map<String, String> availableHierarchies = locationHierarchyService.getAvailableHierarchiesForSubjectType(subjectType);
        String locationHierarchy = availableHierarchies.keySet().iterator().next();

        String[] headers = subjectHeadersCreator.getAllHeaders(formMapping, locationHierarchy);

        assertTrue(containsHeader(headers, "\"Concept \"\"With\"\" Quotes\""),
                "Header for concept with quotes should have internal quotes escaped as double-double-quotes");
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
