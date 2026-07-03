package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.Subject;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
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
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EncounterCreatorIntegrationTest extends BaseCSVImportTest {
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
    private EncounterRepository encounterRepository;

    private EncounterType encounterType;
    private EncounterType encounterTypeWithoutCancellationForm;
    private Concept decisionConcept;

    private String[] validScheduleVisitHeader() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE
        );
    }

    private String[] validUploadVisitHeader() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
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
                "ENC-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().toString("yyyy-MM-dd")
        );
    }

    private String[] validScheduleVisitDataRowForFutureDates() {
        return dataRow(
                "ENC-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().plusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(10).toString("yyyy-MM-dd")
        );
    }

    private String[] invalidScheduleVisitDataRowForFutureDates() {
        return dataRow(
                "ENC-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().plusDays(15).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(10).toString("yyyy-MM-dd")
        );
    }

    private String[] validUploadVisitDataRow() {
        return dataRow(
                "ENC-002",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }


    private String[] invalidScheduleVisitDataRow_SubjectNotFound() {
        return dataRow(
                "ENC-001",
                "SUB-ABC",
                encounterType.getName(),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().toString("yyyy-MM-dd"),
                "21.5135243,85.6731848"
        );
    }

    private String[] invalidUploadVisitDataRow_IdShouldBeUnique() {
        return dataRow(
                "ENC-002",
                "SUB-001",
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
                "ENC-004",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().plusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] invalidUploadVisitDataRow_InvalidConcepts() {
        return dataRow(
                "ENC-005",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Invalid Answer",
                "\"Invalid Answer 1\", \"Invalid Answer 2\"",
                "not-a-number"
        );
    }

    private String[] validCancelledVisitHeaderWithCancellationConcepts() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE,
                EncounterHeadersCreator.CANCEL_DATE,
                EncounterHeadersCreator.CANCEL_LOCATION,
                "\"Cancel Reason Single Select\""
        );
    }

    private String[] validCancelledVisitDataRow() {
        return dataRow(
                "ENC-CAN-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(1).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Cancel Reason Answer 1"
        );
    }

    private String[] cancelledVisitHeader_WithoutCancellationConcepts() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE,
                EncounterHeadersCreator.CANCEL_DATE,
                EncounterHeadersCreator.CANCEL_LOCATION
        );
    }

    private String[] cancelledVisitDataRow_NoCancellationForm() {
        return dataRow(
                "ENC-CAN-NOFORM-001",
                "SUB-001",
                encounterTypeWithoutCancellationForm.getName(),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(1).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848"
        );
    }

    private String[] cancelledVisitDataRow_FutureCancelDate() {
        return dataRow(
                "ENC-CAN-002",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(1).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Cancel Reason Answer 1"
        );
    }

    private String[] cancelledVisitDataRow_CancelDateBeforeEarliest() {
        return dataRow(
                "ENC-CAN-003",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(1).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(7).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Cancel Reason Answer 1"
        );
    }

    private String[] uploadVisitHeader_WithScheduleWindow() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
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
                "ENC-WIN-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(20).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] uploadVisitDataRow_VisitDateOutsideScheduleWindow() {
        return dataRow(
                "ENC-WIN-OUTSIDE-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(20).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(50).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] uploadVisitDataRow_MaxBeforeEarliest() {
        return dataRow(
                "ENC-WIN-BAD-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(20).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123"
        );
    }

    private String[] cancelledVisitHeader_WithDisallowedVisitDate() {
        return header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                EncounterHeadersCreator.MAX_VISIT_DATE,
                EncounterHeadersCreator.CANCEL_DATE,
                EncounterHeadersCreator.VISIT_DATE
        );
    }

    @Override
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation("example", "User Group 1");
        setUser(organisationData.getUser());

        // Create subject type with a unique name
        String subjectTypeName = "SubjectType_" + UUID.randomUUID().toString().substring(0, 8);
        SubjectType subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName(subjectTypeName).build());

        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));

        // Create encounter type with unique name and UUID
        String uniqueEncounterTypeName = "Encounter Type " + UUID.randomUUID().toString().substring(0, 8);
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
        String formName = "Test Encounter Form " + UUID.randomUUID().toString().substring(0, 8);
        org.avni.server.application.FormMapping encounterFormMapping = testFormService.createEncounterForm(
                subjectType,
                encounterType,
                formName,
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList())
        );

        // Attach a decision concept to the visit form (imported as a normal observation, not rule-computed)
        decisionConcept = testConceptService.createCodedConcept("Weight for Age status", "Normal", "Moderate", "Severe");
        testFormService.addDecisionConcepts(encounterFormMapping.getForm().getId(), decisionConcept);

        // Create cancellation form mapping for the same encounter type
        Concept cancelReasonConcept = testConceptService.createCodedConcept("Cancel Reason Single Select",
                "Cancel Reason Answer 1", "Cancel Reason Answer 2");
        String cancellationFormName = "Test Encounter Cancellation Form " + UUID.randomUUID().toString().substring(0, 8);
        testFormService.createGeneralEncounterCancellationForm(
                subjectType,
                encounterType,
                cancellationFormName,
                List.of(cancelReasonConcept.getName()),
                List.of()
        );

        // Create a second encounter type without a cancellation form mapping (defensive lenient path)
        encounterTypeWithoutCancellationForm = new EncounterTypeBuilder()
                .withName("Encounter Type No Cancel Form " + UUID.randomUUID().toString().substring(0, 8))
                .withUuid(UUID.randomUUID().toString())
                .build();
        String noCancelFormName = "Test Encounter Form No Cancel " + UUID.randomUUID().toString().substring(0, 8);
        testFormService.createEncounterForm(
                subjectType,
                encounterTypeWithoutCancellationForm,
                noCancelFormName,
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                multiSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList())
        );

        // Create test subject
        Individual subject = new SubjectBuilder()
                .withRegistrationDate(LocalDate.now().minusDays(10))
                .withSubjectType(subjectType)
                .withFirstName("Test Subject")
                .withLegacyId("SUB-001")
                .build();
        individualRepository.save(subject);
    }

    @Test
    public void testScheduleVisit_Failure_ForUploadVisitData() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("these fields are not needed when scheduling a visit."));
    }

    @Test
    public void testUploadVisit_Failure_ForScheduleVisitData() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertEquals("value required for mandatory field: 'visit date'", exception.getMessage().toLowerCase());
    }

    @Test
    public void testScheduleVisit_Success() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());

        // Verify
        Encounter encounter = encounterRepository.findByLegacyId("ENC-001");
        assertNotNull(encounter);
        assertEquals("SUB-001", encounter.getIndividual().getLegacyId());
        assertEquals(encounterType.getName(), encounter.getEncounterType().getName());
        assertNull(encounter.getEncounterDateTime());
        assertNotNull(encounter.getEarliestVisitDateTime());
        assertNotNull(encounter.getMaxVisitDateTime());
        assertEquals(0, encounter.getObservations().size());
    }

    @Test
    public void testScheduleVisitInFuture_Success() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRowForFutureDates();

        // Execute
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());

        // Verify
        Encounter encounter = encounterRepository.findByLegacyId("ENC-001");
        assertNotNull(encounter);
        assertEquals("SUB-001", encounter.getIndividual().getLegacyId());
        assertEquals(encounterType.getName(), encounter.getEncounterType().getName());
        assertNull(encounter.getEncounterDateTime());
        assertNotNull(encounter.getEarliestVisitDateTime());
        assertNotNull(encounter.getMaxVisitDateTime());
        assertEquals(0, encounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_Success() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // Execute
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Verify
        Encounter encounter = encounterRepository.findByLegacyId("ENC-002");
        assertNotNull(encounter);
        assertEquals("SUB-001", encounter.getIndividual().getLegacyId());
        assertEquals(encounterType.getName(), encounter.getEncounterType().getName());
        assertNotNull(encounter.getEncounterDateTime());
        assertNull(encounter.getEarliestVisitDateTime());
        assertNull(encounter.getMaxVisitDateTime());
        assertEquals(3, encounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_Success_WithDecisionConcept() throws Exception {
        String[] headers = header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.ENCOUNTER_COORDINATES,
                "\"Single Select Coded\"",
                "\"Multi Select Coded\"",
                "\"Numeric Concept\"",
                "\"Weight for Age status\""
        );
        String[] dataRow = dataRow(
                "ENC-DEC-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "SSC Answer 1",
                "\"MSC Answer 1\", \"MSC Answer 2\"",
                "123",
                "Moderate"
        );

        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        Encounter encounter = encounterRepository.findByLegacyId("ENC-DEC-001");
        assertNotNull(encounter);
        assertEquals(4, encounter.getObservations().size());
        assertTrue("Decision concept value must be stored as a normal observation",
                encounter.getObservations().containsKey(decisionConcept.getUuid()));
    }

    @Test
    public void testUploadVisit_FailsWithInvalidDecisionConceptAnswer() {
        String[] headers = header(
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.ENCOUNTER_COORDINATES,
                "\"Weight for Age status\""
        );
        String[] dataRow = dataRow(
                "ENC-DEC-002",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848",
                "Not A Valid Status"
        );

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("invalid answer 'not a valid status'"));
        assertTrue(message, message.contains("weight for age status"));
    }

    @Test
    public void testUploadVisit_FailsWithFutureDate() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_FutureDate();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("'visit date'"));
        assertTrue(message, message.contains("cannot be in future"));
    }

    @Test
    public void testScheduleVisit_FailsWithMaxDateBeforeEarliestDate() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRowForFutureDates();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("max visit date needs to be after earliest visit date"));
    }

    @Test
    public void testUploadVisit_FailsWithInvalidConcepts() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_InvalidConcepts();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        // Verify all validation errors are reported
        String errorMessage = exception.getMessage().toLowerCase();
        assertTrue(errorMessage.contains("invalid answer 'invalid answer'"));
        assertTrue(errorMessage.contains("invalid answer 'invalid answer 1'"));
        assertTrue(errorMessage.contains("invalid value 'not-a-number'"));
    }

    @Test
    public void testDuplicateEncounter_Fails() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // First insert should succeed
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }

    @Test
    public void testScheduleVisit_FailsWithSubjectNotFound() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRow_SubjectNotFound();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("not found in database"));
    }

    @Test
    public void testUploadVisit_FailsWithDuplicateId() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // First insert should succeed
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Try to insert another record with the same ID
        String[] duplicateDataRow = invalidUploadVisitDataRow_IdShouldBeUnique();

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, duplicateDataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }

    @Test
    public void testUploadVisit_Success_WithOptionalScheduleWindow() throws Exception {
        String[] headers = uploadVisitHeader_WithScheduleWindow();
        String[] dataRow = uploadVisitDataRow_WithScheduleWindow();

        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        Encounter encounter = encounterRepository.findByLegacyId("ENC-WIN-001");
        assertNotNull(encounter);
        assertNotNull("Visit date must be set", encounter.getEncounterDateTime());
        assertNotNull("Earliest visit date from schedule window must be preserved", encounter.getEarliestVisitDateTime());
        assertNotNull("Max visit date from schedule window must be preserved", encounter.getMaxVisitDateTime());
        assertEquals(3, encounter.getObservations().size());
    }

    @Test
    public void testUploadVisit_Success_VisitDateOutsideScheduleWindow() throws Exception {
        String[] headers = uploadVisitHeader_WithScheduleWindow();
        String[] dataRow = uploadVisitDataRow_VisitDateOutsideScheduleWindow();

        // Visit Date is intentionally before Earliest Visit Date — no cross-check, must succeed
        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        Encounter encounter = encounterRepository.findByLegacyId("ENC-WIN-OUTSIDE-001");
        assertNotNull(encounter);
        assertNotNull(encounter.getEncounterDateTime());
        assertNotNull(encounter.getEarliestVisitDateTime());
        assertNotNull(encounter.getMaxVisitDateTime());
        assertTrue("Visit date is outside the schedule window — that's allowed",
                encounter.getEncounterDateTime().isBefore(encounter.getEarliestVisitDateTime()));
    }

    @Test
    public void testUploadVisit_Fails_MaxVisitDateBeforeEarliest() {
        String[] headers = uploadVisitHeader_WithScheduleWindow();
        String[] dataRow = uploadVisitDataRow_MaxBeforeEarliest();

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("max visit date needs to be after earliest visit date"));
    }

    @Test
    public void testCancelledVisit_Success_WithCancellationObservations() throws Exception {
        String[] headers = validCancelledVisitHeaderWithCancellationConcepts();
        String[] dataRow = validCancelledVisitDataRow();

        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());

        Encounter encounter = encounterRepository.findByLegacyId("ENC-CAN-001");
        assertNotNull(encounter);
        assertNull("Cancelled encounter must have no visit date", encounter.getEncounterDateTime());
        assertNotNull("Cancel date must be set", encounter.getCancelDateTime());
        assertNotNull("Cancel location must be set", encounter.getCancelLocation());
        assertNotNull("Earliest visit date must be preserved", encounter.getEarliestVisitDateTime());
        assertNotNull("Max visit date must be preserved", encounter.getMaxVisitDateTime());
        assertEquals("Regular observations must be empty for cancelled visits", 0, encounter.getObservations().size());
        assertNotNull("Cancellation observations must be set", encounter.getCancelObservations());
        assertEquals(1, encounter.getCancelObservations().size());
    }

    @Test
    public void testCancelledVisit_Success_NoCancellationFormMapping() throws Exception {
        String[] headers = cancelledVisitHeader_WithoutCancellationConcepts();
        String[] dataRow = cancelledVisitDataRow_NoCancellationForm();

        encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());

        Encounter encounter = encounterRepository.findByLegacyId("ENC-CAN-NOFORM-001");
        assertNotNull(encounter);
        assertNull(encounter.getEncounterDateTime());
        assertNotNull(encounter.getCancelDateTime());
        assertEquals(0, encounter.getObservations().size());
        assertEquals(0, encounter.getCancelObservations().size());
    }

    @Test
    public void testCancelledVisit_FailsWithFutureCancelDate() {
        String[] headers = validCancelledVisitHeaderWithCancellationConcepts();
        String[] dataRow = cancelledVisitDataRow_FutureCancelDate();

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("'cancel date'"));
        assertTrue(message, message.contains("cannot be in future"));
    }

    @Test
    public void testCancelledVisit_FailsWithCancelDateBeforeEarliestVisit() {
        String[] headers = validCancelledVisitHeaderWithCancellationConcepts();
        String[] dataRow = cancelledVisitDataRow_CancelDateBeforeEarliest();

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("'cancel date'"));
        assertTrue(message, message.contains("on or after"));
    }

    @Test
    public void testCancelledVisit_RejectsVisitDateColumn() {
        String[] headers = cancelledVisitHeader_WithDisallowedVisitDate();
        String[] dataRow = dataRow(
                "ENC-CAN-BAD-001",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().minusDays(10).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(2).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(1).toString("yyyy-MM-dd"),
                LocalDate.now().minusDays(5).toString("yyyy-MM-dd")
        );

        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.createForSubject(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_CANCELLED_VISIT.getValue());
        });

        String message = exception.getMessage().toLowerCase();
        assertTrue(message, message.contains("cancelled visit"));
        assertTrue(message, message.contains("visit date"));
    }
}
