package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.Subject;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.creator.EncounterCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ProgramEncounterCreatorIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private EncounterCreator encounterCreator;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;

    private SubjectType subjectType;
    private Program program;
    private EncounterType encounterType;
    private Individual subject;
    private ProgramEnrolment programEnrolment;
    private ProgramEnrolment exitedProgramEnrolment;

    private String[] validScheduleVisitHeader() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE
        );
    }

    private String[] validUploadVisitHeader() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.ENCOUNTER_COORDINATES,
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Numeric Concept\""
        );
    }

    private String[] validScheduleVisitDataRow() {
        return dataRow(
                "PENC-001",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().toString("yyyy-MM-dd")
        );
    }

    private String[] validScheduleVisitDataRowForFutureDates() {
        return dataRow(
                "PENC-001",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().plusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(10).toString("yyyy-MM-dd")
        );
    }


    private String[] invalidScheduleVisitDataRowForFutureDates() {
        return dataRow(
                "PENC-001",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().plusDays(15).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(10).toString("yyyy-MM-dd")
        );
    }

    private String[] validUploadVisitDataRow() {
        return dataRow(
                "PENC-002",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] invalidScheduleVisitDataRow_EnrolmentNotFound() {
        return dataRow(
                "PENC-001",
                "PENR-ABC",
                encounterType.getName(),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().toString("yyyy-MM-dd"),
                "21.5135243,85.6731848"
        );
    }

    private String[] invalidUploadVisitDataRow_IdShouldBeUnique() {
        return dataRow(
                "PENC-002",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] invalidUploadVisitDataRow_FutureDate() {
        return dataRow(
                "PENC-004",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().plusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] invalidUploadVisitDataRow_BeforeEnrolmentDate() {
        return dataRow(
                "PENC-005",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(15).toString("yyyy-MM-dd"), // Before enrolment date
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] validCancelledVisitHeader() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE,
                EncounterHeadersCreator.CANCEL_DATE,
                EncounterHeadersCreator.CANCEL_LOCATION,
                "\"Program Cancel Reason\""
        );
    }

    private String[] validCancelledVisitDataRow(String legacyId, String enrolmentLegacyId) {
        return dataRow(
                legacyId,
                enrolmentLegacyId,
                encounterType.getName(),
                LocalDate.now().minusDays(8).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(1).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "PCR Answer 1"
        );
    }

    private String[] cancelledVisitDataRow_CancelDateBeforeEnrolment(String legacyId) {
        return dataRow(
                legacyId,
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(15).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(12).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(14).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "PCR Answer 1"
        );
    }

    private String[] uploadVisitHeader_WithScheduleWindow() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.ENCOUNTER_COORDINATES,
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Numeric Concept\""
        );
    }

    private String[] uploadVisitDataRow_WithScheduleWindow() {
        return dataRow(
                "PENC-WIN-001",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(8).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(3).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] uploadVisitDataRow_MaxBeforeEarliest() {
        return dataRow(
                "PENC-WIN-BAD-001",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(3).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(8).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] invalidUploadVisitDataRow_InvalidConcepts() {
        return dataRow(
                "PENC-006",
                "PENR-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Invalid Answer",
                "\"Invalid Answer 1\", \"Invalid Answer 2\"",
                "not-a-number"
        );
    }

    @Override
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation("example", "User Group 1");
        setUser(organisationData.getUser());

        // Create subject type with a unique name
        String subjectTypeName = "SubjectType_" + UUID.randomUUID().toString().substring(0, 8);
        subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName(subjectTypeName).build());

        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));

        // Create program with unique name
        String programName = "Program_" + UUID.randomUUID().toString().substring(0, 8);
        program = programRepository.save(new ProgramBuilder()
                .withName(programName)
                .withUuid(UUID.randomUUID().toString())
                .build());

        operationalProgramRepository.save(OperationalProgram.fromProgram(program));

        // Create encounter type with unique name and UUID
        String uniqueEncounterTypeName = "Program Encounter Type " + UUID.randomUUID().toString().substring(0, 8);
        encounterType = new EncounterTypeBuilder()
                .withName(uniqueEncounterTypeName)
                .withUuid(UUID.randomUUID().toString())
                .build();

        // Create concepts
        List<Concept> singleSelectConcepts = new ArrayList<>();
        List<Concept> multiSelectConcepts = new ArrayList<>();

        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded",
                "SSC Answer 1", "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);

        Concept multiSelectCoded = testConceptService.createCodedConcept("Multi Select Coded",
                "MSC Answer 1", "MSC Answer 2", "MSC Answer 3");
        multiSelectConcepts.add(multiSelectCoded);

        Concept numericConcept = testConceptService.createNumericConceptWithAbsolutes("Numeric Concept", 1.0, 200.0);
        singleSelectConcepts.add(numericConcept);

        // Create form mapping with unique name
        String formName = "Test Program Encounter Form " + UUID.randomUUID().toString().substring(0, 8);
        testFormService.createProgramEncounterForm(
                subjectType,
                program,
                encounterType,
                formName,
                singleSelectConcepts.stream().map(Concept::getName).collect(java.util.stream.Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(java.util.stream.Collectors.toList())
        );

        // Create program-encounter cancellation form mapping for the same encounter type
        Concept programCancelReasonConcept = testConceptService.createCodedConcept("Program Cancel Reason",
                "PCR Answer 1", "PCR Answer 2");
        String cancellationFormName = "Test Program Encounter Cancellation Form " + UUID.randomUUID().toString().substring(0, 8);
        testFormService.createProgramEncounterCancellationForm(
                subjectType,
                program,
                encounterType,
                cancellationFormName,
                List.of(programCancelReasonConcept.getName()),
                List.of()
        );

        // Create test subject
        subject = new SubjectBuilder()
                .withRegistrationDate(LocalDate.now().minusDays(20))
                .withSubjectType(subjectType)
                .withFirstName("Test Subject")
                .withLegacyId("SUB-001")
                .withUUID(UUID.randomUUID().toString())
                .build();
        individualRepository.save(subject);

        // Create program enrolment
        programEnrolment = new ProgramEnrolmentBuilder()
                .setProgram(program)
                .setIndividual(subject)
                .withMandatoryFieldsForNewEntity()
                .setEnrolmentDateTime(LocalDateTime.now().minusDays(10).toDateTime())
                .setLegacyId("PENR-001")
                .build();
        programEnrolmentRepository.save(programEnrolment);

        // Create a second enrolment for the same subject that is already exited
        // (used to test: cancelled visits can be created against an exited enrolment)
        exitedProgramEnrolment = new ProgramEnrolmentBuilder()
                .setProgram(program)
                .setIndividual(subject)
                .withMandatoryFieldsForNewEntity()
                .setEnrolmentDateTime(LocalDateTime.now().minusDays(30).toDateTime())
                .setProgramExitDateTime(LocalDateTime.now().minusDays(5).toDateTime())
                .setLegacyId("PENR-EXITED-001")
                .build();
        programEnrolmentRepository.save(exitedProgramEnrolment);
    }

    @Test
    public void testScheduleVisit_Failure_ForUploadVisitData() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("these fields are not needed when scheduling a visit"));
    }

    @Test
    public void testUploadVisit_Failure_ForScheduleVisitData() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertEquals("value required for mandatory field: 'visit date'", exception.getMessage().toLowerCase());
    }

    @Test
    public void testScheduleVisit_Success() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());

        // Verify
        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-001");
        assertNotNull(programEncounter);
        assertEquals("PENR-001", programEncounter.getProgramEnrolment().getLegacyId());
        assertEquals(encounterType.getName(), programEncounter.getEncounterType().getName());
        assertNull(programEncounter.getEncounterDateTime());
        assertNotNull(programEncounter.getEarliestVisitDateTime());
        assertNotNull(programEncounter.getMaxVisitDateTime());
        assertEquals(0, programEncounter.getObservations().size());
    }

    @Test
    public void testScheduleVisitInFuture_Success() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRowForFutureDates();

        // Execute
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());

        // Verify
        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-001");
        assertNotNull(programEncounter);
        assertEquals("PENR-001", programEncounter.getProgramEnrolment().getLegacyId());
        assertEquals(encounterType.getName(), programEncounter.getEncounterType().getName());
        assertNull(programEncounter.getEncounterDateTime());
        assertNotNull(programEncounter.getEarliestVisitDateTime());
        assertNotNull(programEncounter.getMaxVisitDateTime());
        assertEquals(0, programEncounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_Success() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // Execute
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Verify
        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-002");
        assertNotNull(programEncounter);
        assertEquals("PENR-001", programEncounter.getProgramEnrolment().getLegacyId());
        assertEquals(encounterType.getName(), programEncounter.getEncounterType().getName());
        assertNotNull(programEncounter.getEncounterDateTime());
        assertNull(programEncounter.getEarliestVisitDateTime());
        assertNull(programEncounter.getMaxVisitDateTime());
        assertEquals(3, programEncounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_FailsWithFutureDate() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_FutureDate();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("cannot be in future"));
    }

    @Test
    public void testUploadVisit_FailsWithDateBeforeEnrolment() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_BeforeEnrolmentDate();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("visit date needs to be after program enrolment date"));
    }

    @Test
    public void testScheduleVisit_FailsWithMaxDateBeforeEarliestDate() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRowForFutureDates();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("max visit date needs to be after earliest visit date"));
    }

    @Test
    public void testUploadVisit_FailsWithInvalidConcepts() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_InvalidConcepts();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        // Verify all validation errors are reported
        String errorMessage = exception.getMessage().toLowerCase();
        assertTrue(errorMessage.contains("invalid answer 'invalid answer'"));
        assertTrue(errorMessage.contains("invalid answer 'invalid answer 1'"));
        assertTrue(errorMessage.contains("invalid value 'not-a-number'"));
    }

    @Test
    public void testDuplicateProgramEncounter_Fails() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // First insert should succeed
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }

    @Test
    public void testScheduleVisit_FailsWithEnrolmentNotFound() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRow_EnrolmentNotFound();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("not found in database"));
    }

    @Test
    public void testUploadVisit_FailsWithDuplicateId() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // First insert should succeed
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Try to insert another record with the same ID
        String[] duplicateDataRow = invalidUploadVisitDataRow_IdShouldBeUnique();

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, duplicateDataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }

    @Test
    public void testUploadVisit_Success_WithOptionalScheduleWindow() throws Exception {
        String[] headers = uploadVisitHeader_WithScheduleWindow();
        String[] dataRow = uploadVisitDataRow_WithScheduleWindow();

        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-WIN-001");
        assertNotNull(programEncounter);
        assertNotNull("Visit date must be set", programEncounter.getEncounterDateTime());
        assertNotNull("Earliest visit date from schedule window must be preserved", programEncounter.getEarliestVisitDateTime());
        assertNotNull("Max visit date from schedule window must be preserved", programEncounter.getMaxVisitDateTime());
        assertEquals(3, programEncounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_Fails_MaxVisitDateBeforeEarliest() {
        String[] headers = uploadVisitHeader_WithScheduleWindow();
        String[] dataRow = uploadVisitDataRow_MaxBeforeEarliest();

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("max visit date needs to be after earliest visit date"));
    }

    @Test
    public void testCancelledVisit_Success_WithCancellationObservations() throws Exception {
        String[] headers = validCancelledVisitHeader();
        String[] dataRow = validCancelledVisitDataRow("PENC-CAN-001", "PENR-001");

        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());

        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-CAN-001");
        assertNotNull(programEncounter);
        assertNull("Cancelled program encounter must have no visit date", programEncounter.getEncounterDateTime());
        assertNotNull("Cancel date must be set", programEncounter.getCancelDateTime());
        assertNotNull("Cancel location must be set", programEncounter.getCancelLocation());
        assertNotNull(programEncounter.getEarliestVisitDateTime());
        assertNotNull(programEncounter.getMaxVisitDateTime());
        assertEquals(0, programEncounter.getObservations().size());
        assertNotNull(programEncounter.getCancelObservations());
        assertEquals(1, programEncounter.getCancelObservations().size());
    }

    @Test
    public void testCancelledVisit_Allowed_AgainstExitedEnrolment() throws Exception {
        String[] headers = validCancelledVisitHeader();
        String[] dataRow = validCancelledVisitDataRow("PENC-CAN-EXIT-001", "PENR-EXITED-001");

        // Should succeed — cancellation against an exited enrolment is intentionally allowed
        encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());

        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyId("PENC-CAN-EXIT-001");
        assertNotNull(programEncounter);
        assertEquals("PENR-EXITED-001", programEncounter.getProgramEnrolment().getLegacyId());
        assertNotNull(programEncounter.getProgramEnrolment().getProgramExitDateTime());
        assertNotNull(programEncounter.getCancelDateTime());
    }

    @Test
    public void testCancelledVisit_FailsWhenCancelDateBeforeEnrolmentDate() {
        String[] headers = validCancelledVisitHeader();
        String[] dataRow = cancelledVisitDataRow_CancelDateBeforeEnrolment("PENC-CAN-002");

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForEnrolment(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("'cancel date'"));
    }
}
