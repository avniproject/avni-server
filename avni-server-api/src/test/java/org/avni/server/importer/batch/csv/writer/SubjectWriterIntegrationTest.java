package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.*;
import org.junit.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SubjectWriterIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private SubjectWriter subjectWriter;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private SubjectHeadersCreator subjectHeadersCreator;
    private FormMapping registrationFormMapping;

    @Override
    public void setUp() throws Exception {
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(district, state));
        List<Concept> singleSelectConcepts = new ArrayList<>();
        List<Concept> multiSelectConcepts = new ArrayList<>();
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1", "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);
        Concept multiSelectCoded = testConceptService.createCodedConcept("Multi Select Coded", "MSC Answer 1", "MSC Answer 2", "MSC Answer 3", "MSC Answer 4");
        multiSelectConcepts.add(multiSelectCoded);
        singleSelectConcepts.add(testConceptService.createConcept("Date Concept", ConceptDataType.Date));
        singleSelectConcepts.add(testConceptService.createConcept("Text Concept", ConceptDataType.Text));
        singleSelectConcepts.add(testConceptService.createConcept("Numeric Concept", ConceptDataType.Numeric));

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setName("SubjectType1").build());

        registrationFormMapping = testFormService.createRegistrationForm(subjectType, "Registration Form", FormType.IndividualProfile,
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()));

        AddressLevel bihar = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District1").parent(bihar).type(district).build());
        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District2").parent(bihar).type(district).build());
    }

    @Test
    public void shouldCreateUpdate() throws Exception {
        System.out.println(Arrays.toString(subjectHeadersCreator.getAllHeaders(registrationFormMapping)));
        System.out.println(Arrays.toString(subjectHeadersCreator.getAllDescriptions(registrationFormMapping)));

        // new subject
        String[] header = header("Id from previous system", "Subject Type", "Date Of Registration", "Registration Location", "First Name", "Last Name", "Date Of Birth", "Date Of Birth Verified", "Gender", "State", "District", "Single Select Coded", "Multi Select Coded", "Date Concept", "Text Concept", "Numeric Concept");
        String[] dataRow = dataRow("1", "SubjectType1", "2020-01-01", "21.5135243,85.6731848", "John", "Doe", "1990-01-01", "true", "Male", "Bihar", "District1", "SSC Answer 1", "\"MSC Answer 1\", \"MSC Answer 2\"", "2020-01-01", "text", "123", "1");

        subjectWriter.write(Chunk.of(new Row(header, dataRow)));
    }
}
