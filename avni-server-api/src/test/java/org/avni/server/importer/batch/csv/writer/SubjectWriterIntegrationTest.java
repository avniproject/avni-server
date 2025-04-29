package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestLocationService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private SubjectWriter subjectWriter;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private OrganisationConfigService organisationConfigService;

    private String[] validHeader() {
        return header("Id from previous system",
                "Subject Type",
                "Date Of Registration",
                "Registration Location",
                "First Name",
                "Last Name",
                "Date Of Birth",
                "Date Of Birth Verified",
                "Gender",
                "Profile Picture",
                "State",
                "District",
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Date Concept\"",
                "\"Text Concept\"",
                "\"Numeric Concept\"",
                "\"Notes Concept\"",
                "\"Multi Select Decision Coded\"",
                "\"QuestionGroup Concept|QG Text Concept\"",
                "\"QuestionGroup Concept|QG Numeric Concept\"",
                "Repeatable QuestionGroup Concept|RQG Numeric Concept|1"
                );
    }

    private String[] validHeaderWithMultipleRQGValues() {
        return header("Id from previous system",
                "Subject Type",
                "Date Of Registration",
                "Registration Location",
                "First Name",
                "Last Name",
                "Date Of Birth",
                "Date Of Birth Verified",
                "Gender",
                "Profile Picture",
                "State",
                "District",
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Date Concept\"",
                "\"Text Concept\"",
                "\"Numeric Concept\"",
                "\"Notes Concept\"",
                "\"Multi Select Decision Coded\"",
                "\"QuestionGroup Concept|QG Text Concept\"",
                "\"QuestionGroup Concept|QG Numeric Concept\"",
                "Repeatable QuestionGroup Concept|RQG Numeric Concept|1",
                "Repeatable QuestionGroup Concept|RQG Text Concept|1",
                "Repeatable QuestionGroup Concept|RQG Numeric Concept|2",
                "Repeatable QuestionGroup Concept|RQG Text Concept|2"
                );
    }

    private String[] headerWithDuplicates() {
        return header("Id from previous system",
                "Subject Type",
                "Date Of Registration",
                "Registration Location",
                "First Name",
                "Last Name",
                "Date Of Birth",
                "Date Of Birth Verified",
                "Gender",
                "Profile Picture",
                "State",
                "District",
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Date Concept\"",
                "\"Text Concept\"",
                "\"Text Concept\"",
                "\"Numeric Concept\"",
                "\"Notes Concept\"",
                "\"Multi Select Decision Coded\"",
                "\"QuestionGroup Concept|QG Text Concept\"",
                "\"QuestionGroup Concept|QG Text Concept\"",
                "\"QuestionGroup Concept|QG Numeric Concept\"",
                "Repeatable QuestionGroup Concept|RQG Numeric Concept|1"
        );
    }
    private String[] validDataRow() {
        return dataRow("ABCD",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "123",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789");
    }

    private String[] validDataRowWithMultipleRQGValues() {
        return dataRow("ABCD",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "123",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789",
                "rqg text1",
                "987",
                "rqg text2");
    }

    private String[] validDataRowWithoutLegacyId() {
        return dataRow("",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "123",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789");
    }

    private String[] dataRowWithWrongValues() {
        return dataRow("ABCD",
                "SubjectType1",
                "2090-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1 Invalid",
                "\"MSC Answer 1\", \"MSC Answer 2 Invalid\"",
                "shouldHaveBeenADate",
                "text",
                "shouldHaveBeenANumber",
                "some notes",
                "\"MSDC Aswer 1\", \"MSDC Answer 2\"",
                "qg text",
                "shouldHaveBeenANumber",
                "789");
    }

    private String[] dataRowWithNumericValuesOutsideValidRange() {
        return dataRow("",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "500",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789");
    }

    private String[] dataRowWithMissingMandatoryValues() {
        return dataRow("",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "",
                "",
                "",
                "",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789");
    }

    private String[] dataRowWithMissingMandatoryQGValues() {
        return dataRow("",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "100",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "",
                "",
                "");
    }

    private String[] dataRowWithCodedAnswersInDifferentCase() {
        return dataRow("ABCD",
                "SubjectType1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "1990-01-01",
                "true",
                "Male",
                "",
                "Bihar",
                "District1",
                "ssc answer 1",
                "\"msc answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "123",
                "some notes",
                "\"MSDC Answer 1\", \"msdc answer 2\"",
                "qg text",
                "456",
                "123");
    }

    @Test
    public void missingMandatoryCoreValues() {
        String[] dataRow = dataRow("ABCD",
                "SubjectType1",
                "",
                "21.5135243,85.6731848",
                "John",
                "Doe",
                "",
                "true",
                "",
                "",
                "Bihar",
                "District1",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "2020-01-01",
                "text",
                "123",
                "some notes",
                "\"MSDC Answer 1\", \"MSDC Answer 2\"",
                "qg text",
                "456",
                "789");
        failure(validHeader(), dataRow, "value required for mandatory field: 'date of birth', value required for mandatory field: 'date of registration', value required for mandatory field: 'gender'");
    }

    @Test
    public void headerWithWrongFields() {
        failure(header("Id from previou system",
                        "Subject Type",
                        "Date Of Registratio",
                        "Registration Location",
                        "First Name",
                        "Last Name",
                        "Date Of Birt",
                        "Date Of Birth Verified",
                        "Gender",
                        "Profile Picture",
                        "State",
                        "Distric",
                        "\"Singl Select Coded\"",
                        "\"Multi Select Coded\"",
                        "\"Date Concept\"",
                        "\"Text Concept\"",
                        "\"Numeric Concept\"",
                        "\"Notes Concept\"",
                        "\"Multi Selec Decision Coded\"",
                        "\"QuestionGroup Concept|QG Text Concept\"",
                        "\"QuestionGroup Concept|QG Numeric Concept\"",
                        "Repeatable QuestionGroup Concept|RQG Numeric Concept|1"
                ),
                validDataRow(),
                "mandatory columns are missing in header from uploaded file - date of registration, district, date of birth. please refer to sample file for the list of mandatory headers. unknown headers - distric, date of birt, id from previou system, date of registratio, singl select coded, multi selec decision coded included in file. please refer to sample file for valid list of headers.");
    }

    @Test
    public void allowHeaderWithSpaces() throws ValidationException, InvalidConfigurationException {
        String[] headers = header(" Id from previous system ",
                "Subject Type",
                "Date Of Registration ",
                "Registration Location ",
                " First Name",
                "Last Name",
                "Date Of Birth",
                " Date Of Birth Verified",
                "Gender",
                " Profile Picture",
                "State",
                " District",
                " \"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Date Concept\"",
                "\"Text Concept\"",
                "\"Numeric Concept\"",
                "\"Notes Concept\"",
                " \"Multi Select Decision Coded\"",
                "\"QuestionGroup Concept|QG Text Concept\"",
                "\"QuestionGroup Concept|QG Numeric Concept\"",
                "Repeatable QuestionGroup Concept|RQG Numeric Concept|1"
        );
        String[] dataRow = validDataRow();
        subjectWriter.write(Chunk.of(new Row(headers, dataRow)));
        Individual subject = individualRepository.findByLegacyId("ABCD");
        ObservationCollection observations = subject.getObservations();
        assertEquals(9, observations.size());
        assertEquals("John", subject.getFirstName());
    }

    @Override
    public void setUp() throws Exception {
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID())
                .build();
        AddressLevelType district = new AddressLevelTypeBuilder().parent(state).name("District").level(3d).withUuid(UUID.randomUUID())
                .build();
        testDataSetupService.saveLocationTypes(Arrays.asList(state, district));
        List<Concept> singleSelectConcepts = new ArrayList<>();
        List<Concept> multiSelectConcepts = new ArrayList<>();
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1",
                "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);
        Concept multiSelectCoded = testConceptService.createCodedConcept("Multi Select Coded", "MSC Answer 1",
                "MSC Answer 2", "MSC Answer 3", "MSC Answer 4");
        Concept multiSelectDecisionCoded = testConceptService.createCodedConcept("Multi Select Decision Coded", "MSDC Answer 1", "MSDC Answer 2", "MSDC Answer 3", "MSDC Answer 4");
        multiSelectConcepts.add(multiSelectCoded);
        singleSelectConcepts.add(testConceptService.createConcept("Date Concept", ConceptDataType.Date));
        singleSelectConcepts.add(testConceptService.createConcept("Text Concept", ConceptDataType.Text));
        singleSelectConcepts.add(testConceptService.createNumericConceptWithAbsolutes("Numeric Concept", 1.0, 200.0));
        singleSelectConcepts.add(testConceptService.createConcept("Notes Concept", ConceptDataType.Notes));

        SubjectType subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName("SubjectType1").build());
        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));

        Concept questionGroupConcept = testConceptService.createConcept("QuestionGroup Concept", ConceptDataType.QuestionGroup);
        List<Concept> childQGConcepts = new ArrayList<>();
        childQGConcepts.add(testConceptService.createConcept("QG Numeric Concept", ConceptDataType.Numeric));
        childQGConcepts.add(testConceptService.createConcept("QG Text Concept", ConceptDataType.Text));

        Concept repeatableQuestionGroupConcept = testConceptService.createConcept("Repeatable QuestionGroup Concept", ConceptDataType.QuestionGroup);
        List<Concept> childRQGConcepts = new ArrayList<>();
        childRQGConcepts.add(testConceptService.createConcept("RQG Numeric Concept", ConceptDataType.Numeric));
        childRQGConcepts.add(testConceptService.createConcept("RQG Text Concept", ConceptDataType.Text));
        FormMapping registrationFormMapping = testFormService.createRegistrationForm(subjectType, "Registration Form",
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                questionGroupConcept,
                childQGConcepts,
                repeatableQuestionGroupConcept,
                childRQGConcepts);
        testFormService.addDecisionConcepts(registrationFormMapping.getForm().getId(), multiSelectDecisionCoded);

        AddressLevel bihar = testLocationService
                .save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District1").parent(bihar).type(district).build());
        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District2").parent(bihar).type(district).build());

        organisationConfigService.saveRegistrationLocation(district, subjectType);
    }

    @Test
    public void shouldCreateUpdate() throws ValidationException, InvalidConfigurationException {
        // new subject
        String[] header = validHeader();
        String[] dataRow = validDataRow();

        subjectWriter.write(Chunk.of(new Row(header, dataRow)));
        Individual subject = individualRepository.findByLegacyId("ABCD");
        assertEquals(9, subject.getObservations().size());
        assertEquals("John", subject.getFirstName());

        // allow edit
        subjectWriter.write(Chunk.of(new Row(header, dataRow)));
        subject = individualRepository.findByLegacyId("ABCD");
        assertEquals(9, subject.getObservations().size());
        assertEquals("John", subject.getFirstName());
    }

    private void success(String[] headers, String[] values) throws InvalidConfigurationException {
        try {
            long previousCount = individualRepository.count();
            subjectWriter.write(Chunk.of(new Row(headers, values)));
            assertEquals(previousCount + 1, individualRepository.count());
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void allowWithoutLegacyId() throws InvalidConfigurationException {
        success(validHeader(), validDataRowWithoutLegacyId());
        success(validHeader(), validDataRowWithoutLegacyId());
    }

    @Test
    public void allowCodedAnswersInDifferentCase() throws InvalidConfigurationException {
        success(validHeader(), dataRowWithCodedAnswersInDifferentCase());
    }

    @Test
    public void shouldFailValidationIfObservationValuesAreWrong() {
        failure(validHeader(),
                dataRowWithWrongValues(),
                "'Date Of Registration' 2090-01-01 is in future, Invalid answer 'MSC Answer 2 Invalid' for 'Multi Select Coded', Invalid answer 'MSDC Aswer 1' for 'Multi Select Decision Coded', Invalid answer 'SSC Answer 1 Invalid' for 'Single Select Coded', Invalid value 'shouldHaveBeenADate' for 'Date Concept', Invalid value 'shouldHaveBeenANumber' for 'Numeric Concept', Invalid value 'shouldhavebeenanumber' for 'QG Numeric Concept'");
    }

    @Test
    public void shouldNotFailValidationIfMandatoryQuestionGroupFieldsAreNotProvided() throws InvalidConfigurationException {
        success(validHeader(), dataRowWithMissingMandatoryQGValues());
    }

    @Test
    public void shouldFailValidationIfNumericValueIsOutsideValidRange() {
        failure(validHeader(), dataRowWithNumericValuesOutsideValidRange(), "Invalid answer '500' for 'Numeric Concept'");
    }

    @Test
    public void shouldFailValidationIfDuplicateHeadersArePresent() {
        failure(headerWithDuplicates(), validDataRow(), "Headers Text Concept, QuestionGroup Concept|QG Text Concept are repeated. Please update the name or remove the duplicates.");
    }

    @Test
    public void shouldHandleMultipleRQGValues() throws InvalidConfigurationException {
        success(validHeaderWithMultipleRQGValues(), validDataRowWithMultipleRQGValues());
        Individual subject = individualRepository.findByLegacyId("ABCD");
        assertEquals(9, subject.getObservations().size());

    }
    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = individualRepository.count();
        try {
            subjectWriter.write(Chunk.of(new Row(headers, cells)));
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals(errorMessage.toLowerCase(), e.getMessage().toLowerCase());
        }
        long after = individualRepository.count();
        Assert.assertEquals(before, after);
    }
}
