package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.*;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.anyOf;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ProgramEnrolmentHeadersCreatorIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;

    @Autowired
    private FormMappingRepository formMappingRepository;

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;

    @Autowired
    private TestProgramService testProgramService;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private TestDataSetupService testDataSetupService;

    @Autowired
    TestConceptService testConceptService;

    private FormMapping formMapping;
    private Program program;

    @Before
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .build());

        program = new ProgramBuilder()
                .withName("TestProgram")
                .build();

        formMapping = testProgramService.addProgramAndGetFormMapping(program, subjectType);
    }

    @Test
    public void testBasicHeaderGeneration() {
        String[] headers = programEnrolmentHeadersCreator.getAllHeaders(formMapping);
        String[] description = programEnrolmentHeadersCreator.getAllDescriptions(formMapping);

        assertNotNull(headers);
        assertThat(headers.length, greaterThan(0));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.id));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.subjectId));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.enrolmentDate));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.exitDate));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.enrolmentLocation));
        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.exitLocation));

        assertThat(description, hasItemInArray("\"| Optional | Can be used to later identify the entry |\""));
        assertThat(description, hasItemInArray("\"| Mandatory | UUID of the subject to be enrolled. Can be identified from address bar in Data Entry App or Longitudinal export file |\""));
        assertThat(description, hasItemInArray("\"| Optional | Format: DD-MM-YYYY |\""));
        assertThat(description, hasItemInArray("\"| Optional | Format: (21.5135243,85.6731848) |\""));

    }

    @Test
    public void testHeadersWithConceptFields() {
        Concept textConcept = testConceptService.createConcept("Text,Concept", ConceptDataType.Text);
        Concept notesConcept = testConceptService.createConcept("Notes Concept", ConceptDataType.Notes);
        Concept numericConcept = testConceptService.createConcept("Numeric Concept", ConceptDataType.Numeric);
        numericConcept.setHighAbsolute(5.0);
        numericConcept.setLowAbsolute(2.0);
        numericConcept.setActive(true);
        Concept dateConcept = testConceptService.createConcept("Date Concept", ConceptDataType.Date);
        Concept codedConceptSingleSelect = testConceptService.createCodedConcept("Coded Concept Single Select", "Answer 1", "Answer 2");
        Concept codedConceptMultiSelect = testConceptService.createCodedConcept("Coded Concept Multi Select", "Ans 1", "Ans 2");

        Concept readOnlyFormElementConcept = testConceptService.createConcept("Text Concept (This should remain a single cell , not a different cell)", ConceptDataType.Text);
        FormElement readOnlyFormElement = new TestFormElementBuilder()
                .withDisplayOrder(2d)
                .withConcept(readOnlyFormElementConcept)
                .withName(readOnlyFormElementConcept.getName())
                .withReadOnly(false)
                .withUuid(UUID.randomUUID().toString())
                .build();

        Form testForm = new TestFormBuilder()
                .withUuid(UUID.randomUUID().toString())
                .withFormType(FormType.ProgramEnrolment)
                .withName("ProgramEnrolment Form").addFormElementGroup(
                        new TestFormElementGroupBuilder()
                                .withDisplayOrder(1d)
                                .withUuid(UUID.randomUUID().toString())
                                .addFormElement(
                                        new TestFormElementBuilder().withDisplayOrder(2d).withConcept(textConcept).withName(textConcept.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(2d).withConcept(notesConcept).withName(notesConcept.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(2d).withConcept(numericConcept).withName(numericConcept.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(2d).withConcept(dateConcept).withName(dateConcept.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(1d).withType(FormElementType.SingleSelect).withConcept(codedConceptSingleSelect).withName(codedConceptSingleSelect.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        new TestFormElementBuilder().withDisplayOrder(1d).withType(FormElementType.MultiSelect).withConcept(codedConceptMultiSelect).withName(codedConceptMultiSelect.getName()).withUuid(UUID.randomUUID().toString()).build(),
                                        readOnlyFormElement

                                )
                                .withName("ProgramEnrolment Form Element Group")
                                .build()
                ).build();
        formRepository.save(testForm);

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .build());

        FormMapping newFormMapping = new FormMappingBuilder()
                .withUuid(UUID.randomUUID().toString())
                .withForm(testForm)
                .withProgram(program)
                .withSubjectType(subjectType)
                .build();
        formMappingRepository.save(newFormMapping);

        formRepository.save(testForm);

        String[] headers = programEnrolmentHeadersCreator.getAllHeaders(newFormMapping);
        String[] description = programEnrolmentHeadersCreator.getAllDescriptions(newFormMapping);

        assertThat(headers, hasItemInArray("\"Text,Concept\""));
        assertThat(headers, hasItemInArray("\"Notes Concept\""));
        assertThat(headers, hasItemInArray("\"Numeric Concept\""));
        assertThat(headers, hasItemInArray("\"Date Concept\""));
        assertThat(headers, hasItemInArray("\"Coded Concept Single Select\""));
        assertThat(headers, hasItemInArray("\"Coded Concept Multi Select\""));

        assertThat(description, hasItemInArray("\"| Optional | Any Text |\""));
        assertThat(description, hasItemInArray("\"| Optional | Format: DD-MM-YYYY |\""));
        assertThat(description, hasItemInArray("\"| Optional | Format: (21.5135243,85.6731848) |\""));

        assertThat(Arrays.asList(description), hasItem(anyOf(
                equalTo("\"| Optional | Allowed values: {Ans 2, Ans 1} Format: Separate multiple values by a comma. |\""),
                equalTo("\"| Optional | Allowed values: {Ans 1, Ans 2} Format: Separate multiple values by a comma. |\"")
        )));
        assertThat(Arrays.asList(description), hasItem(anyOf(
                equalTo("\"| Optional | Allowed values: {Answer 1, Answer 2} Only single value allowed. |\""),
                equalTo("\"| Optional | Allowed values: {Answer 2, Answer 1} Only single value allowed. |\"")
        )));
        assertThat(description, hasItemInArray("\"| Optional | Min value allowed: 2.0 Max value allowed: 5.0 |\""));
        assertThat(description, hasItemInArray("\"| Optional | Any Text | The value can be auto-calculated if not entered |\""));
    }

    @Test
    public void testDescriptionsGeneration() {
        String[] descriptions = programEnrolmentHeadersCreator.getAllDescriptions(formMapping);

        assertNotNull(descriptions);
        assertEquals(programEnrolmentHeadersCreator.getAllHeaders(formMapping).length, descriptions.length,
                "Descriptions array should match headers array length");
    }

}
