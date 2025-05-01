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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
                EncounterHeadersCreator.ENCOUNTER_LOCATION,
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

    private String[] invalidScheduleVisitDataRow_FutureDates() {
        return dataRow(
                "ENC-003",
                "SUB-001",
                encounterType.getName(),
                LocalDate.now().plusDays(5).toString("yyyy-MM-dd"),
                LocalDate.now().plusDays(10).toString("yyyy-MM-dd"),
                "21.5135243,85.6731848"
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
        testFormService.createEncounterForm(
                subjectType,
                encounterType,
                formName,
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
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("these fields are not needed when scheduling a visit."));
    }

    @Test
    public void testUploadVisit_Failure_ForScheduleVisitData() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertEquals("unknown headers - earliest visit date, max visit date included in file. please refer to sample file for valid list of headers.", exception.getMessage().toLowerCase());
    }

    @Test
    public void testScheduleVisit_Success() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = validScheduleVisitDataRow();

        // Execute
        encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());

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
        encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

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
    public void testScheduleVisit_SucceedsWithFutureDates() {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRow_FutureDates();

        // Execute and verify
        assertDoesNotThrow(() -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

    }

    @Test
    public void testUploadVisit_FailsWithFutureDate() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_FutureDate();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("'visit date' cannot be in future"));
    }

    @Test
    public void testUploadVisit_FailsWithInvalidConcepts() {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = invalidUploadVisitDataRow_InvalidConcepts();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
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
        encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }

    @Test
    public void testScheduleVisit_FailsWithSubjectNotFound() throws Exception {
        String[] headers = validScheduleVisitHeader();
        String[] dataRow = invalidScheduleVisitDataRow_SubjectNotFound();

        // Execute and verify
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.SCHEDULE_VISIT.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("not found in database"));
    }

    @Test
    public void testUploadVisit_FailsWithDuplicateId() throws Exception {
        String[] headers = validUploadVisitHeader();
        String[] dataRow = validUploadVisitDataRow();

        // First insert should succeed
        encounterCreator.create(new Row(headers, dataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());

        // Try to insert another record with the same ID
        String[] duplicateDataRow = invalidUploadVisitDataRow_IdShouldBeUnique();

        // Second insert with same legacy ID should fail
        Exception exception = assertThrows(ValidationException.class, () -> {
            encounterCreator.create(new Row(headers, duplicateDataRow), EncounterUploadMode.UPLOAD_VISIT_DETAILS.getValue());
        });

        assertTrue(exception.getMessage().toLowerCase().contains("already present in avni"));
    }
}
