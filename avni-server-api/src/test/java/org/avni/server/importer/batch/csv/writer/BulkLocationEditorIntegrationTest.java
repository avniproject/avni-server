package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.*;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.*;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BulkLocationEditorIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private FormRepository formRepository;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private BulkLocationEditor bulkLocationEditor;
    @Autowired
    private LocationRepository locationRepository;

    @Override
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Collections.singletonList(state));
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).parent(state).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Collections.singletonList(district));
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).parent(district).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Collections.singletonList(block));
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

        AddressLevel bihar = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District1").parent(bihar).type(district).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block11").parent(district1).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block12").parent(district1).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block13").parent(district1).type(block).build());

        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District2").parent(bihar).type(district).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block21").parent(district2).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block22").parent(district2).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block23").parent(district2).type(block).build());

        AddressLevel district3 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District3").parent(bihar).type(district).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block31").parent(district3).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block32").parent(district3).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block33").parent(district3).type(block).build());

        setUser(organisationData.getUser().getUsername());
    }

    private void lineageExists(String... lineage) {
        Optional<AddressLevel> address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(", ", lineage));
        assertTrue(address.isPresent());
    }

    private void lineageNotExists(String... lineage) {
        Optional<AddressLevel> address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(", ", lineage));
        assertFalse(address.isPresent());
    }

    private String[] verifyExists(String... strings) {
        return strings;
    }

    private String[] verifyNotExists(String... strings) {
        return strings;
    }

    private void treatAsDescriptor(String[] headers, String... descriptorCells) {
        long before = locationRepository.count();
        bulkLocationEditor.write(Collections.singletonList(new Row(headers, descriptorCells)));
        long after = locationRepository.count();
        assertEquals(before, after);
    }

    private void success(String[] headers, String[] dataRow, String[] exists, String[] ... notExists) {
        bulkLocationEditor.editLocation(new Row(headers, dataRow), new ArrayList<>());
        lineageExists(exists);
        for (String[] notExist : notExists) {
            lineageNotExists(notExist);
        }
    }

    private void failure(String[] headers, String[] dataRow, String errorMessage, String[] exists, String[] ... notExists) {
        try {
            bulkLocationEditor.editLocation(new Row(headers, dataRow), new ArrayList<>());
            fail();
        } catch (Exception exception) {
            assertEquals(errorMessage, exception.getMessage());
        }
        lineageExists(exists);
        for (String[] notExist : notExists) {
            lineageNotExists(notExist);
        }
    }

    private void failsOnMissingHeader(String[] headers, String... errorMessages) {
        try {
            bulkLocationEditor.write(Collections.singletonList(new Row(headers, new String[0])));
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

    @Test
    public void shouldEdit() {
        // no change
        success(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District1, Block11", "Block11", "Bihar, District1", "23.45,43.85"),
                verifyExists("Bihar", "District1", "Block11"));

        // no change with spaces
        success(header(" Location with full hierarchy ", " New location name", " Parent location with full hierarchy", " GPS coordinates"),
                dataRow("Bihar, District1, Block11", "Block11", "Bihar, District1", "23.45,43.85"),
                verifyExists("Bihar", "District1", "Block11"));

        // change name
        success(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District1, Block11", "Block11toNew", "Bihar, District1", "23.45,43.85"),
                verifyExists("Bihar", "District1", "Block11toNew"),
                verifyNotExists("Bihar, District1, Block11"));

        // change parent
        success(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District1, Block12", "Block24", "Bihar, District2", "23.45,43.85"),
                verifyExists("Bihar", "District2", "Block24"),
                verifyNotExists("Bihar, District1, Block12"));

        // existing location in different case
        success(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District1, Block13", "Block13new", "bihar, district1", "23.45,43.85"),
                verifyExists("Bihar", "District1", "Block13new"),
                verifyNotExists("Bihar, District1, Block13"));

        // lineage with spaces
        failure(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar,  District3,  Block31", " Block31New", "Bihar,  District3", "23.45,43.85"),
                error("Provided Location does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations 'Bihar,  District3,  Block31'"),
                verifyExists("Bihar", "District3", "Block31"),
                verifyNotExists("Bihar, District3, Block31New"));

        // lineage without spaces
        failure(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar,District3,Block31", " Block31New", "Bihar,  District3", "23.45,43.85"),
                error("Provided Location does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations 'Bihar,District3,Block31'"),
                verifyExists("Bihar", "District3", "Block31"),
                verifyNotExists("Bihar, District3, Block31New"));

        // change to non existing parent
        failure(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District2, Block21", " Block21Town", "Bihar,  DistrictN", "23.45,43.85"),
                error("Provided new location parent does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations - 'Bihar,  DistrictN'"),
                verifyExists("Bihar", "District2", "Block21"),
                verifyNotExists("Bihar, District2, Block21Town"));

        // change to parent of a different type from allowed parent's type
        failure(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Bihar, District2, Block21", " Block21Town", "Bihar", "23.45,43.85"),
                error("Only parent of location type 'District' is allowed for Block21."),
                verifyExists("Bihar", "District2", "Block21"),
                verifyNotExists("Bihar, District2, Block21Town"));

        // attempt to change root level location to an invalid parent
        failure(header("Location with full hierarchy", "Parent location with full hierarchy"),
                dataRow("Bihar", "Bihar, District2"),
                error("No parent is allowed for Bihar since it is a top level location."),
                verifyExists("Bihar"),
                verifyNotExists("District2, Bihar"));

        treatAsDescriptor(header("Location with full hierarchy", "New location name", "Parent location with full hierarchy", "GPS coordinates"),
                dataRow("Can be found from Admin -> Locations -> Click Export. Used to specify which location's fields need to be updated. mandatory field",
                        "Enter new name here ONLY if it needs to be updated",
                        "Hierarchy of parent location that should contain the child location",
                        "Example: 23.45,43.85"));

        // missing header - nothing provided
        failsOnMissingHeader(
                header(),
                hasError("'Location with full hierarchy' is required, At least one of 'New location name', 'GPS coordinates' or 'Parent location with full hierarchy' is required")
        );
        // missing header - New location name
        failsOnMissingHeader(
                header("Location with full hierarchy"),
                hasError("At least one of 'New location name', 'GPS coordinates' or 'Parent location with full hierarchy'")
        );
    }
}
