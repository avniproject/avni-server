package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.*;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNull;
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
    private SubjectHeadersCreator subjectHeadersCreator;
    @Autowired
    private IndividualRepository individualRepository;
    private FormMapping registrationFormMapping;

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
                "\"Notes Concept\"");
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
                "1",
                "some notes");
    }

    private String[] dataRowWithWrongValues() {
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
                "SSC Answer 1 Invalid",
                "\"MSC Answer 1\", \"MSC Answer 2 Invalid\"",
                "shouldHaveBeenADate",
                "text",
                "shouldHaveBeenANumber",
                "some notes");
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
                        "\"Notes Concept\""),
                validDataRow(),
                "Mandatory columns are missing from uploaded file - Single Select Coded, Date Of Birth, Date Of Registration, District, Id from previous system. Please refer to sample file for the list of mandatory headers. Unknown headers - Date Of Birt, Singl Select Coded, Id from previou system, Date Of Registratio, Distric included in file. Please refer to sample file for valid list of headers.");
    }

    @Test
    public void allowHeaderWithSpaces() throws ValidationException {
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
                "\"Notes Concept\"");
        String[] dataRow = validDataRow();
        subjectWriter.write(Chunk.of(new Row(headers, dataRow)));
        Individual subject = individualRepository.findByLegacyId("ABCD");
        ObservationCollection observations = subject.getObservations();
        assertEquals(6, observations.size());
        assertEquals("John", subject.getFirstName());
    }

    @Override
    public void setUp() throws Exception {
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID())
                .build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID())
                .build();
        testDataSetupService.saveLocationTypes(Arrays.asList(district, state));
        List<Concept> singleSelectConcepts = new ArrayList<>();
        List<Concept> multiSelectConcepts = new ArrayList<>();
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1",
                "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);
        Concept multiSelectCoded = testConceptService.createCodedConcept("Multi Select Coded", "MSC Answer 1",
                "MSC Answer 2", "MSC Answer 3", "MSC Answer 4");
        multiSelectConcepts.add(multiSelectCoded);
        singleSelectConcepts.add(testConceptService.createConcept("Date Concept", ConceptDataType.Date));
        singleSelectConcepts.add(testConceptService.createConcept("Text Concept", ConceptDataType.Text));
        singleSelectConcepts.add(testConceptService.createConcept("Numeric Concept", ConceptDataType.Numeric));
        singleSelectConcepts.add(testConceptService.createConcept("Notes Concept", ConceptDataType.Notes));

        SubjectType subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName("SubjectType1").build());
        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));

        registrationFormMapping = testFormService.createRegistrationForm(subjectType, "Registration Form",
                FormType.IndividualProfile,
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()));

        AddressLevel bihar = testLocationService
                .save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District1").parent(bihar).type(district).build());
        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District2").parent(bihar).type(district).build());
    }

    @Test
    public void shouldCreateUpdate() throws ValidationException {
        // new subject
        String[] header = validHeader();
        String[] dataRow = validDataRow();

        subjectWriter.write(Chunk.of(new Row(header, dataRow)));
        Individual subject = individualRepository.findByLegacyId("ABCD");
        assertEquals(6, subject.getObservations().size());
        assertEquals("John", subject.getFirstName());
    }

    @Test
    public void shouldFailValidationIfObservationValuesAreWrong() {
        failure(validHeader(),
                dataRowWithWrongValues(),
                "Invalid answer 'MSC Answer 2 Invalid' for 'Multi Select Coded', Invalid answer 'SSC Answer 1 Invalid' for 'Single Select Coded', Invalid value 'shouldHaveBeenADate' for 'Date Concept', Invalid value 'shouldHaveBeenANumber' for 'Numeric Concept'");
    }

    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = individualRepository.count();
        try {
            subjectWriter.write(Chunk.of(new Row(headers, cells)));
            fail();
        } catch (Exception e) {
            Assert.assertEquals(errorMessage, e.getMessage());
        }
        long after = individualRepository.count();
        Assert.assertEquals(before, after);
    }
}
