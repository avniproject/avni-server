package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.Form;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.RuleServerInvoker;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    @Override
    public void setUp() throws Exception {
        RuleServerInvoker ruleServerInvoker = Mockito.mock(RuleServerInvoker.class);
        subjectWriter.setRuleServerInvoker(ruleServerInvoker);
        when(ruleServerInvoker.getRuleServerResult(any(), any(), (Individual) any(), any())).thenReturn(UploadRuleServerResponseContract.nullObject());
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(district, state));
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1", "SSC Answer 2", "SSC Answer 3");
        Concept multiSelectCoded = testConceptService.createCodedConcept("Multi Select Coded", "MSC Answer 1", "MSC Answer 2", "MSC Answer 3", "MSC Answer 4");
        testConceptService.createConcept("Date Concept", ConceptDataType.Date);
        testConceptService.createConcept("Text Concept", ConceptDataType.Text);
        testConceptService.createConcept("Numeric Concept", ConceptDataType.Numeric);
        testConceptService.createConcept("Subject Concept", ConceptDataType.Subject);
        testConceptService.createConcept("DateTime Concept", ConceptDataType.DateTime);
        testConceptService.createConcept("Time Concept", ConceptDataType.Time);
        testConceptService.createConcept("Duration Concept", ConceptDataType.Duration);
        testConceptService.createConcept("Image Concept", ConceptDataType.Image);
        testConceptService.createConcept("Id Concept", ConceptDataType.Id);
        testConceptService.createConcept("Location Concept", ConceptDataType.Location);
        testConceptService.createConcept("PhoneNumber Concept", ConceptDataType.PhoneNumber);
        testConceptService.createConcept("GroupAffiliation Concept", ConceptDataType.GroupAffiliation);
        testConceptService.createConcept("QuestionGroup Concept", ConceptDataType.QuestionGroup);
        testConceptService.createConcept("Encounter Concept", ConceptDataType.Encounter);
        testConceptService.createConcept("Text in QG Concept", ConceptDataType.Text);


        testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setName("SubjectType1").build());

        AddressLevel bihar = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District1").parent(bihar).type(district).build());
        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District2").parent(bihar).type(district).build());
    }

    @Test
    @Ignore
    public void shouldCreateUpdate() throws Exception {
        // new subject
        String[] header = header("Id from previous system", "Subject Type", "Date Of Registration", "Registration Location", "First Name", "Last Name", "Profile Picture", "Date Of Birth", "Date Of Birth Verified", "Gender", "State", "District", "Single Select Coded", "Multi Select Coded", "Date Concept", "Text Concept", "Numeric Concept", "Subject Concept", "DateTime Concept", "Time Concept", "Duration Concept", "Image Concept", "Id Concept", "Location Concept", "PhoneNumber Concept", "GroupAffiliation Concept", "QuestionGroup Concept", "Encounter Concept", "Text in QG Concept");
        String[] dataRow = dataRow("1", "SubjectType1", "2020-01-01", "21.5135243,85.6731848", "John", "Doe", "http://example.com/john.jpg", "1990-01-01", "true", "Male", "Bihar", "District1", "{SSC Answer 1}", "{\"MSC Answer 1\", \"MSC Answer 2\"}", "2020-01-01", "text", "123", "1", "2020-01-01T10:00:00", "10:00:00", "PT10M", "http://example.com/image.jpg", "123", "21.5135243,85.6731848", "123", "123", "123", "123", "123");

        subjectWriter.write(Chunk.of(new Row(header, dataRow)));
    }
}
