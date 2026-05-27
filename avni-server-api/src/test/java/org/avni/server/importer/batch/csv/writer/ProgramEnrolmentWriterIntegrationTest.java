package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.creator.ProgramEnrolmentRowCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestLocationService;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramEnrolmentWriterIntegrationTest extends BaseCSVImportTest {
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
    private ProgramEnrolmentRowCreator programEnrolmentRowCreator;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;

    private String[] validHeader() {
        return header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Coordinates",
                "\"Single Select Coded\"");
    }

    private String[] validDataRow() {
        return dataRow("EFGH",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1");
    }

    private String[] validDataRowWithNoIdFromPreviousSystem() {
        return dataRow("",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1");
    }

    private String[] dataRowWithNoFields() {
        return dataRow("",
                "",
                "",
                "",
                "",
                "");
    }

    private String[] dataRowWithOnlySubjectId() {
        return dataRow("",
                "ABCD",
                "",
                "",
                "",
                "");
    }

    private String[] dataRowWithNoEnrolmentDate() {
        return dataRow("",
                "ABCD",
                "Program1",
                "",
                "",
                "");
    }

    private String[] validDataRowWithoutLegacyId() {
        return dataRow("",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1");
    }

    @Test
    public void headerWithWrongFields() {
        failure(header("Id from previus system",
                        "Subject Id from previous system",
                        "Program",
                        "Enrolent Date",
                        "Enrolment Coordinates",
                        "\"Single SSSelect Coded\""),
                validDataRow(),
                "mandatory columns are missing in header from uploaded file - enrolment date. please refer to sample file for the list of mandatory headers. unknown headers - enrolent date, single ssselect coded, id from previus system included in file. please refer to sample file for valid list of headers.");
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
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1",
                "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);

        SubjectType subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName("SubjectType1").build());
        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));
        Individual subject = new SubjectBuilder()
                .withRegistrationDate(LocalDate.now())
                .withSubjectType(subjectType)
                .withFirstName("John")
                .withLegacyId("ABCD")
                .build();
        individualRepository.save(subject);

        Program program = programRepository.save(new ProgramBuilder().withName("Program1").withUuid(UUID.randomUUID().toString()).build());
        operationalProgramRepository.save(OperationalProgram.fromProgram(program));

        FormMapping enrolmentFormMapping = testFormService.createEnrolmentForm(subjectType, program, "Enrolment Form",
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                new ArrayList<>());

        AddressLevel bihar = testLocationService
                .save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District1").parent(bihar).type(district).build());

        String[] allHeaders = programEnrolmentHeadersCreator.getAllHeaders(enrolmentFormMapping,null);
        System.out.println(String.join(",", allHeaders));
    }

    @Test
    public void shouldCreateUpdate() throws ValidationException, InvalidConfigurationException {
        String[] header = validHeader();
        String[] dataRow = validDataRow();

        programEnrolmentRowCreator.create(new Row(header, dataRow), ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT);
        ProgramEnrolment enrolment = programEnrolmentRepository.findByLegacyId("EFGH");
        assertEquals(1, enrolment.getObservations().size());

        // Second insert should fail with ValidationException
        Exception exception = assertThrows(ValidationException.class, () -> {
            programEnrolmentRowCreator.create(new Row(header, dataRow), ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT);
        });
        assertTrue(exception.getMessage().toLowerCase().contains("entry with id from previous system, efgh already present in avni"));
    }

    private String[] validExitedEnrolmentHeader() {
        return header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Coordinates",
                "\"Single Select Coded\"",
                "Exit Date",
                "Exit Coordinates",
                "\"Exit: Exit Reason\"");
    }

    private String[] validExitedEnrolmentDataRow() {
        return dataRow("EXIT-ENR-001",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "2020-06-01",
                "22.5135243,86.6731848",
                "Exit Reason Answer 1");
    }

    private String[] exitedEnrolmentDataRow_ExitDateBeforeEnrolment() {
        return dataRow("EXIT-ENR-002",
                "ABCD",
                "Program1",
                "2020-06-01",
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "2020-01-01",
                "22.5135243,86.6731848",
                "Exit Reason Answer 1");
    }

    private String[] exitedEnrolmentDataRow_FutureExitDate() {
        return dataRow("EXIT-ENR-003",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1",
                LocalDate.now().plusDays(2).toString("yyyy-MM-dd"),
                "22.5135243,86.6731848",
                "Exit Reason Answer 1");
    }

    private String[] exitedEnrolmentDataRow_MissingExitDate() {
        return dataRow("EXIT-ENR-004",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "",
                "22.5135243,86.6731848",
                "Exit Reason Answer 1");
    }

    private String[] malformedExitPrefixHeader() {
        return header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Coordinates",
                "\"Single Select Coded\"",
                "Exit Date",
                "\"Vitals|Exit: Weight\"");
    }

    private String[] malformedExitPrefixDataRow() {
        return dataRow("EXIT-ENR-005",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "2020-06-01",
                "anything");
    }

    private FormMapping createProgramExitFormForTests() {
        Concept exitReason = testConceptService.createCodedConcept("Exit Reason",
                "Exit Reason Answer 1", "Exit Reason Answer 2");
        SubjectType subjectType = subjectTypeRepository.findByName("SubjectType1");
        Program program = programRepository.findByName("Program1");
        return testFormService.createProgramExitForm(subjectType, program, "Program Exit Form " + UUID.randomUUID().toString().substring(0, 8),
                List.of(exitReason.getName()), List.of());
    }

    @Test
    public void exitedEnrolment_success_persistsBothRegularAndExitObservations() throws ValidationException, InvalidConfigurationException {
        createProgramExitFormForTests();
        programEnrolmentRowCreator.create(new Row(validExitedEnrolmentHeader(), validExitedEnrolmentDataRow()), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);

        ProgramEnrolment enrolment = programEnrolmentRepository.findByLegacyId("EXIT-ENR-001");
        assertNotNull(enrolment);
        assertEquals(1, enrolment.getObservations().size(), "Regular observations from non-exit columns must be set");
        assertNotNull(enrolment.getProgramExitDateTime(), "Exit date must be persisted");
        assertNotNull(enrolment.getExitLocation(), "Exit location must be persisted");
        assertNotNull(enrolment.getProgramExitObservations(), "Exit observations must be persisted");
        assertEquals(1, enrolment.getProgramExitObservations().size(), "Exit observations from Exit:-prefixed columns must be set");
    }

    @Test
    public void exitedEnrolment_failsWhenExitDateBeforeEnrolmentDate() {
        createProgramExitFormForTests();
        try {
            programEnrolmentRowCreator.create(new Row(validExitedEnrolmentHeader(), exitedEnrolmentDataRow_ExitDateBeforeEnrolment()), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);
            fail("Expected ValidationException");
        } catch (Exception e) {
            String message = e.getMessage().toLowerCase();
            assertTrue(message.contains("'exit date'"), message);
            assertTrue(message.contains("on or after"), message);
            assertTrue(message.contains("'enrolment date'"), message);
        }
    }

    @Test
    public void exitedEnrolment_failsWhenExitDateInFuture() {
        createProgramExitFormForTests();
        try {
            programEnrolmentRowCreator.create(new Row(validExitedEnrolmentHeader(), exitedEnrolmentDataRow_FutureExitDate()), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);
            fail("Expected ValidationException");
        } catch (Exception e) {
            String message = e.getMessage().toLowerCase();
            assertTrue(message.contains("'exit date'"), message);
            assertTrue(message.contains("cannot be in future"), message);
        }
    }

    @Test
    public void exitedEnrolment_failsWhenExitDateMissing() {
        createProgramExitFormForTests();
        try {
            programEnrolmentRowCreator.create(new Row(validExitedEnrolmentHeader(), exitedEnrolmentDataRow_MissingExitDate()), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);
            fail("Expected ValidationException");
        } catch (Exception e) {
            String message = e.getMessage().toLowerCase();
            assertTrue(message.contains("'exit date'"), message);
            assertTrue(message.contains("mandatory"), message);
        }
    }

    @Test
    public void exitedEnrolment_rejectsExitPrefixNotAtStartOfHeader() {
        createProgramExitFormForTests();
        try {
            programEnrolmentRowCreator.create(new Row(malformedExitPrefixHeader(), malformedExitPrefixDataRow()), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);
            fail("Expected ValidationException");
        } catch (Exception e) {
            String message = e.getMessage().toLowerCase();
            assertTrue(message.contains("exit:"), message);
            assertTrue(message.contains("not at the start"), message);
        }
    }

    private void success(String[] headers, String[] values) throws InvalidConfigurationException {
        try {
            long previousCount = programEnrolmentRepository.count();
            programEnrolmentRowCreator.create(new Row(headers, values), ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT);
            assertEquals(previousCount + 1, programEnrolmentRepository.count());
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void allowWithoutLegacyId() throws InvalidConfigurationException {
        success(validHeader(), validDataRowWithoutLegacyId());
    }

    @Test
    public void doNotAllowMultipleEnrolmentsIfNotEnabled() throws InvalidConfigurationException {
        success(validHeader(), validDataRowWithNoIdFromPreviousSystem());
        failure(validHeader(), validDataRowWithNoIdFromPreviousSystem(), "subject 'abcd' is already enrolled in program 'program1' and the program doesn't allow for multiple enrolments");
    }

    @Test
    public void noData() {
        failure(validHeader(), dataRowWithNoFields(),
                "'subject id from previous system' is required, program '' not found, subject id '' not found");
        failure(validHeader(), dataRowWithOnlySubjectId(),
                "program '' not found");
        failure(validHeader(), dataRowWithNoEnrolmentDate(),
                "value required for mandatory field: 'enrolment date'");
    }

    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = individualRepository.count();
        try {
            programEnrolmentRowCreator.create(new Row(headers, cells), ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals(errorMessage.toLowerCase(), e.getMessage().toLowerCase());
        }
        long after = individualRepository.count();
        Assert.assertEquals(before, after);
    }

    private Program createProgramWithEnrolmentForm(String programName, String formName, List<String> singleSelectConceptNames) {
        SubjectType subjectType = subjectTypeRepository.findByName("SubjectType1");
        Program program = programRepository.save(new ProgramBuilder().withName(programName).withUuid(UUID.randomUUID().toString()).build());
        operationalProgramRepository.save(OperationalProgram.fromProgram(program));
        testFormService.createEnrolmentForm(subjectType, program, formName, singleSelectConceptNames, new ArrayList<>());
        return program;
    }

    @Test
    public void defaultEnrolment_allowsConceptNameContainingExitPrefixSubstring() throws ValidationException, InvalidConfigurationException {
        // A ProgramEnrolment-form concept whose name contains "Exit: " (not at the start) must not be
        // rejected in default upload_enrolments mode — the misplaced-prefix check is exited-mode only.
        Concept embeddedExit = testConceptService.createCodedConcept("Reason for Exit: Other", "Embedded Answer 1", "Embedded Answer 2");
        createProgramWithEnrolmentForm("ProgramEmbeddedExit", "Enrolment Form With Embedded Exit", List.of(embeddedExit.getName()));

        String[] header = header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Coordinates",
                "\"Reason for Exit: Other\"");
        String[] dataRow = dataRow("DEFAULT-EMBEDDED-001",
                "ABCD",
                "ProgramEmbeddedExit",
                "2020-01-01",
                "21.5135243,85.6731848",
                "Embedded Answer 1");

        programEnrolmentRowCreator.create(new Row(header, dataRow), ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT);

        ProgramEnrolment enrolment = programEnrolmentRepository.findByLegacyId("DEFAULT-EMBEDDED-001");
        assertNotNull(enrolment, "Default-mode enrolment with an 'Exit: ' substring concept must be created");
        assertEquals(1, enrolment.getObservations().size(), "The embedded-prefix concept must be stored as a regular observation");
        assertNull(enrolment.getProgramExitDateTime(), "Default-mode enrolment must not be exited");
    }

    @Test
    public void exitedEnrolment_sharedConcept_storesIndependentValuesUnderSameConceptUuid() throws ValidationException, InvalidConfigurationException {
        // A concept present on both the ProgramEnrolment and ProgramExit forms must keep independent
        // values in observations vs programExitObservations under the same concept UUID.
        SubjectType subjectType = subjectTypeRepository.findByName("SubjectType1");
        Concept sharedConcept = testConceptService.createCodedConcept("Shared Status", "Active", "Closed");
        Program program = createProgramWithEnrolmentForm("ProgramShared", "Shared Enrolment Form", List.of(sharedConcept.getName()));
        testFormService.createProgramExitForm(subjectType, program, "Shared Exit Form " + UUID.randomUUID().toString().substring(0, 8),
                List.of(sharedConcept.getName()), List.of());

        String[] header = header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Coordinates",
                "\"Shared Status\"",
                "Exit Date",
                "Exit Coordinates",
                "\"Exit: Shared Status\"");
        String[] dataRow = dataRow("SHARED-ENR-001",
                "ABCD",
                "ProgramShared",
                "2020-01-01",
                "21.5135243,85.6731848",
                "Active",
                "2020-06-01",
                "22.5135243,86.6731848",
                "Closed");

        programEnrolmentRowCreator.create(new Row(header, dataRow), ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT);

        ProgramEnrolment enrolment = programEnrolmentRepository.findByLegacyId("SHARED-ENR-001");
        assertNotNull(enrolment);
        String sharedConceptUuid = sharedConcept.getUuid();
        String enrolmentValue = enrolment.getObservations().getStringValue(sharedConceptUuid);
        String exitValue = enrolment.getProgramExitObservations().getStringValue(sharedConceptUuid);
        assertNotNull(enrolmentValue, "Shared concept must have a regular-observation value");
        assertNotNull(exitValue, "Shared concept must have an exit-observation value");
        assertNotEquals(enrolmentValue, exitValue, "Shared concept must hold independent values across the two observation sets");
    }
}
