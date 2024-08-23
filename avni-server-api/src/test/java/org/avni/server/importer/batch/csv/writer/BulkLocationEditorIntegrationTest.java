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
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;

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
    private LocationHierarchyService locationHierarchyService;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private BulkLocationEditor bulkLocationEditor;
    private String hierarchy;
    @Autowired
    private LocationRepository locationRepository;

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

        AddressLevel bihar = testLocationService.save(new AddressLevelBuilder().title("Bihar").type(state).withUuid(UUID.randomUUID()).withDefaultValuesForNewEntity().build());
        AddressLevel vaishali = testLocationService.save(new AddressLevelBuilder().title("Vaishali").parent(bihar).type(district).withUuid(UUID.randomUUID()).withDefaultValuesForNewEntity().build());
        testLocationService.save(new AddressLevelBuilder().title("Mahua").parent(vaishali).type(block).withUuid(UUID.randomUUID()).withDefaultValuesForNewEntity().build());
        testLocationService.save(new AddressLevelBuilder().title("Gaya").parent(bihar).type(district).withUuid(UUID.randomUUID()).withDefaultValuesForNewEntity().build());

        setUser(organisationData.getUser().getUsername());
        Map<String, String> hierarchies = locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        hierarchy = String.join(".", hierarchies.keySet());
    }

    private void lineageExists(String... lineage) {
        AddressLevel address = this.locationRepository.findByTitleLineageIgnoreCase(String.join(", ", lineage)).get();
        assertNotNull(address);
    }

    @Test
    @Ignore
    public void shouldEdit() {
        success(header("Location with full hierarchy","New location name","Parent location with full hierarchy","GPS coordinates"),
                dataRow("Bihar, Vaishali, Mahua", "Mahnar", "Bihar, Vaishali", "23.45,43.85"));
        lineageExists("Bihar", "Vaishali", "Mahnar");
    }

    private void success(String[] headers, String[] dataRow) {
        bulkLocationEditor.editLocation(new Row(headers, dataRow), new ArrayList<>());
    }
}
