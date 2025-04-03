package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.*;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BulkLocationCreatorIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private BulkLocationCreator bulkLocationCreator;
    @Autowired
    private LocationHierarchyService locationHierarchyService;
    @Autowired
    private LocationRepository addressLevelRepository;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private FormRepository formRepository;
    @Autowired
    private ConceptRepository conceptRepository;
    private String hierarchy;

    @Override
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).withUuid(UUID.randomUUID()).build();
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(block, district, state));
        Concept codedConcept = testConceptService.createCodedConcept("Coded Concept", "Answer 1", "Answer 2");
        Concept textConcept = testConceptService.createConcept("Text Concept", ConceptDataType.Text);
        Form locationForm = new TestFormBuilder()
                .withUuid(UUID.randomUUID().toString())
                .withFormType(FormType.Location)
                .withName("Location Form")
                .addFormElementGroup(
                        new TestFormElementGroupBuilder()
                                .withDisplayOrder(1d)
                                .withUuid(UUID.randomUUID().toString())
                                .addFormElement(
                                        new TestFormElementBuilder().withDisplayOrder(1d).withType(FormElement.SINGLE_SELECT).withConcept(codedConcept).withName(codedConcept.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(2d).withConcept(textConcept).withName(textConcept.getName()).withUuid(UUID.randomUUID().toString()).build()
                                )
                                .withName("Location Form Element Group")
                                .build()
                ).build();
        formRepository.save(locationForm);

        setUser(organisationData.getUser().getUsername());
        Map<String, String> hierarchies = locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        hierarchy = String.join(".", hierarchies.keySet());
    }

    private static int newLocationsCreated(int count) {
        return count;
    }

    private void assertLineageExists(String... lineage) {
        String titleLineage = String.join(", ", lineage);
        AddressLevel address = this.locationRepository.findByTitleLineageIgnoreCase(titleLineage).get();
        assertNotNull(titleLineage, address);
    }

    private void locationHasAttribute(String[] lineage, String conceptName) {
        AddressLevel address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(", ", lineage)).get();
        Concept concept = conceptRepository.findByName(conceptName);
        assertNotNull(address.getLocationProperties().get(concept.getUuid()));
    }

    private void treatAsDescriptor(String[] headers, String... additionalHeaders) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, additionalHeaders)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }

    private void success(String[] headers, String[] cells, int numberOfNewLocations, String[]... lineages) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before + newLocationsCreated(numberOfNewLocations), after);
        Arrays.stream(lineages).forEach(this::assertLineageExists);
    }

    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = addressLevelRepository.count();
        try {
            bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
            fail();
        } catch (Exception e) {
            assertEquals(errorMessage, e.getMessage());
        }
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }

    private void failsOnMissingHeader(String[] headers, String... errorMessages) {
        try {
            bulkLocationCreator.write(Collections.singletonList(new Row(headers, new String[0])), hierarchy);
            fail();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message == null) {
                e.printStackTrace();
                fail();
            } else {
                Arrays.stream(errorMessages).forEach(s -> {
                    if (!message.contains(s)) {
                        e.printStackTrace();
                        fail("Expected error message: " + s + " not present in: " + message);
                    }
                });
            }
        }
    }

    private String[] lineageExists(String... lineage) {
        return lineage;
    }

    @Test
    public void shouldCreate() {
        // three locations, full lineage created
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Mahua", "23.45,43.85"),
                newLocationsCreated(3),
                lineageExists("Bihar", "Vaishali", "Mahua")
        );

        // Location with space
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Vaishali ", " Jamui ", "23.20,43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "Vaishali", "Jamui")
                );
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Darbhanga "),
                newLocationsCreated(1),
                lineageExists("Bihar", "Darbhanga"));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Aara ", " ", "24.20,43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "Aara"));
        success(header(" State ", "District", " Block", " GPS coordinates"),
                dataRow(" Bihar", " Chapra ", " ", "24.20,43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "Chapra"));

        // gps with space
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "District1", "", "24.20, 43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "District1"));

        // upper case
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" bihar", " VAISHALI ", " Tarora ", "23.20,43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "Vaishali", "Tarora"));

        failure(header("State1", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                error("Location types missing or not in order in header for specified Location Hierarchy. Please refer to sample file for valid list of headers."));
        failure(header("State", "District2", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                error("Location types missing or not in order in header for specified Location Hierarchy. Please refer to sample file for valid list of headers."));
        failure(header("District", "State", "Block", "GPS coordinates"),
                dataRow("Vaishali", "Bihar", "Nijma", "23.45,43.85"),
                error("Location types missing or not in order in header for specified Location Hierarchy. Please refer to sample file for valid list of headers."));

        // invalid header for GPS coordinates
        failure(header("State", "District", "Block", "GPS"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                error("Unknown headers included in file. Please refer to sample file for valid list of headers."));
        // invalid GPS coordinates
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,"),
                error("Invalid 'GPS coordinates'"));
        // invalid GPS coordinates
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45a,"),
                error("Invalid 'GPS coordinates'"));

        //attributes
        success(header("State", "District", "Block", "GPS coordinates", "\"Coded Concept\""),
                dataRow("Bihar", "Vaishali", "Block 1", "23.45,43.86", "Answer 1"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 1"), "Coded Concept");
        //attributes with space in header
        success(header("State", "District", "Block", "GPS coordinates", " \"Coded Concept\""),
                dataRow("Bihar", "Vaishali", "Block 2", "23.45,43.86", " Answer 1"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 2"), "Coded Concept");

        //attributes with text concept type
        success(header("State", "District", "Block", "GPS coordinates", "\"Text Concept\""),
                dataRow("Bihar", "Vaishali", "Block 3", "23.45,43.86", "any text"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 3"), "Text Concept");

        failure(header("State", "District", "Block", "GPS coordinates", "\"Coded Concept\" "),
                dataRow("Bihar", "Vaishali", "Block 4", "23.45,43.86", "not an answer to this concept"),
                error("Invalid answer 'not an answer to this concept' for 'Coded Concept'"));

        // multiple attributes
        success(header("State", "District", "Block", "GPS coordinates", " \"Coded Concept\"", "\"Text Concept\""),
                dataRow("Bihar", "Vaishali", "Block 5", "23.45,43.86", " Answer 1", "any text"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 5"), "Coded Concept");
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 5"), "Text Concept");


        // without full hierarchy
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "District 1", " ", "23.45,43.85"),
                newLocationsCreated(1),
                lineageExists("Bihar", "District 1"));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("State 2", "District 1", " ", "23.45,43.85"),
                newLocationsCreated(2),
                lineageExists("State 2", "District 1"));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("State 3", " ", " ", "23.45,43.85"),
                newLocationsCreated(1),
                lineageExists("State 3"));


        // if done in random steps
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" ", " ", "Block11", "23.45,43.85"),
                error("Parent missing for location provided"));
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" ", " District 11", "Block11", "23.45,43.85"),
                error("Parent missing for location provided"));
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" ", " ", " ", "23.45,43.85"),
                error("No location provided"));
        // end


        // create existing location, results in no new location created
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Mahua", "23.45,43.85"),
                newLocationsCreated(0));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", " Mahua ", "23.45,43.85"),
                newLocationsCreated(0));

        // missing headers
        failsOnMissingHeader(
                header(),
                hasError("Location types missing or not in order in header for specified Location Hierarchy. Please refer to sample file for valid list of headers.")
        );

        treatAsDescriptor(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Example: state 1", "Example: distr 1", "Example: blo 1", "Ex. 23.45,43.85"));
        treatAsDescriptor(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Example: state 1", "Example: distr 1 ", "Example: blo 1", " Ex. 23.45,43.85 "));
        treatAsDescriptor(header("State", "District", "Block", "GPS coordinates"),
                dataRow("  state 1", "Example: distr 1 ", "Example: blo 1", " Ex. 23.45,43.85 "));
    }
}
