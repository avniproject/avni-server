package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.avni.server.importer.batch.csv.writer.BulkLocationCreator.LocationTypesHeaderError;
import static org.avni.server.importer.batch.csv.writer.BulkLocationCreator.UnknownHeadersErrorMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    private String hierarchy;

    @Override
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).withUuid(UUID.randomUUID()).build();
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(block, district, state));
        testConceptService.createCodedConcept("Coded Concept", "Answer 1", "Answer 2");
        testConceptService.createConcept("Text Concept", ConceptDataType.Text);

        setUser(organisationData.getUser().getUsername());
        Map<String, String> hierarchies = locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        hierarchy = String.join(".", hierarchies.keySet());
    }

    private static int newLocationsCreated(int count) {
        return count;
    }

    private void lineageExists(String ... lineage) {
        AddressLevel address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(".", lineage)).get();
        assertNotNull(address);
    }

    private void locationHasAttribute(String[] lineage, String concept) {
        AddressLevel address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(".", lineage)).get();
        assertNotNull(address.getLocationProperties().get(concept));
    }

    private void treatAsDescriptor(String[] headers, String ... additionalHeaders) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, additionalHeaders)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }

    private void success(String[] headers, String[] cells, int numberOfNewLocations) {
        long before = addressLevelRepository.count();
        bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
        long after = addressLevelRepository.count();
        assertEquals(before + newLocationsCreated(numberOfNewLocations), after);
    }

    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = addressLevelRepository.count();
        try {
            bulkLocationCreator.write(Collections.singletonList(new Row(headers, cells)), hierarchy);
        } catch (Exception e) {
            assertEquals(errorMessage, e.getMessage());
        }
        long after = addressLevelRepository.count();
        assertEquals(before, after);
    }

    @Test
    @Ignore
    public void shouldCreate() {
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Mahua", "23.45,43.85"),
                newLocationsCreated(3));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Vaishali ", " Jamui ", "23.20,43.85"),
                newLocationsCreated(1));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Darbhanga "),
                newLocationsCreated(1));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Bihar", " Aara ", " ", "24.20,43.85"),
                newLocationsCreated(1));
        success(header(" State ", "District", " Block", " GPS coordinates"),
                dataRow(" Bihar", " Chapra ", " ", "24.20,43.85"),
                newLocationsCreated(1));
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" bihar", " VAISHALI ", " Tarora ", "23.20,43.85"),
                newLocationsCreated(1));

        failure(header("State1", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                LocationTypesHeaderError);
        failure(header("State", "District2", "Block", "GPS coordinates"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                LocationTypesHeaderError);
        failure(header("State", "District", "Block", "GPS"),
                dataRow("Bihar", "Vaishali", "Nijma", "23.45,43.85"),
                UnknownHeadersErrorMessage);

        treatAsDescriptor(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Ex: state 1", "Ex: distr 1", "Ex: blo 1", "Ex. 23.45,43.85"));
        treatAsDescriptor(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" Ex: state 1", "Ex: distr 1 ", "Ex: blo 1", " Ex. 23.45,43.85 "));


        //attributes
        success(header("State", "District", "Block", "GPS coordinates", "Coded Concept"),
                dataRow("Bihar", "Vaishali", "Block 1", "23.45,43.86", "Answer 1"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 1"), "Coded Concept");

        success(header("State", "District", "Block", "GPS coordinates", " Coded Concept"),
                dataRow("Bihar", "Vaishali", "Block 2", "23.45,43.86", " Answer 1"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 2"), "Coded Concept");

        success(header("State", "District", "Block", "GPS coordinates", "Text Concept"),
                dataRow("Bihar", "Vaishali", "Block 3", "23.45,43.86", "any text"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 3"), "Text Concept");

        failure(header("State", "District", "Block", "GPS coordinates", "Coded Concept "),
                dataRow("Bihar", "Vaishali", "Block 4", "23.45,43.86", "not an answer to this concept"),
                "");

        success(header("State", "District", "Block", "GPS coordinates", " Coded Concept", "Text Concept"),
                dataRow("Bihar", "Vaishali", "Block 5", "23.45,43.86", " Answer 1", "any text"),
                newLocationsCreated(1));
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 5"), "Coded Concept");
        locationHasAttribute(lineage("Bihar", "Vaishali", "Block 5"), "Text Concept");
        // end


        // without full hierarchy
        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("Bihar", "District 1", " ", "23.45,43.85"), newLocationsCreated(1));
        lineageExists("Bihar", "District 1");

        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("State 2", "District 1", " ", "23.45,43.85"), newLocationsCreated(2));
        lineageExists("State 2", "District 1");

        success(header("State", "District", "Block", "GPS coordinates"),
                dataRow("State 3", " ", " ", "23.45,43.85"), newLocationsCreated(1));
        lineageExists("State 3");
        // end


        // if in random steps
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" ", " ", "Block11", "23.45,43.85"), "");
        failure(header("State", "District", "Block", "GPS coordinates"),
                dataRow(" ", " District 11", "Block11", "23.45,43.85"), "");
        // end
    }
}
