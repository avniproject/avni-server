package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.service.builder.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ProgramEncounterHeadersCreatorIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private EncounterHeadersCreator encounterHeadersCreator;

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;

    @Autowired
    private TestDataSetupService testDataSetupService;

    @Autowired
    private TestFormService testFormService;

    @Autowired
    private TestConceptService testConceptService;

    @Autowired
    private TestProgramService testProgramService;

    private SubjectType subjectType;
    private Program program;
    private EncounterType encounterType;
    private FormMapping formMapping;

    @Before
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());

        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build()
        );

        String test_program = "Test Program" + java.util.UUID.randomUUID();
        program = testProgramService.addProgram(new ProgramBuilder().withName(test_program).build(), subjectType);

        encounterType = new EncounterTypeBuilder()
                .withName("Test Program Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();
        String formName = "Test Program Encounter Form " + java.util.UUID.randomUUID();
        formMapping = testFormService.createProgramEncounterForm(subjectType, program, encounterType, formName, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void testBasicHeaderGeneration_scheduleVisit() {
        String[] headers = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.SCHEDULE_VISIT);
        System.out.println("Generated headers: " + Arrays.toString(headers));
        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.ID));
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.EARLIEST_VISIT_DATE));
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.MAX_VISIT_DATE));
    }

    @Test
    public void testBasicHeaderGeneration_uploadVisitDetails() {
        String[] headers = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.UPLOAD_VISIT_DETAILS);
        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.ID));
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.VISIT_DATE));
    }

    @Test
    public void testHeaderValidation_failsOnMissingMandatoryHeaders() {
        String[] headers = {EncounterHeadersCreator.VISIT_DATE}; // missing ID
        Exception exception = assertThrows(Exception.class, () -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.SCHEDULE_VISIT);
        });
        assertTrue(exception.getMessage().contains("Mandatory columns are missing"));
    }

    @Test
    public void testHeaderValidation_allowsOptionalHeaders() {
        Concept codedConcept = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1", "SSC Answer 2");
        java.util.List<String> singleSelectConcepts = java.util.Collections.singletonList(codedConcept.getName());
        encounterType = new EncounterTypeBuilder()
                .withName("Program Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();
        String formName = "Test Program Encounter Form " + java.util.UUID.randomUUID();
        formMapping = testFormService.createProgramEncounterForm(subjectType, program, encounterType, formName, singleSelectConcepts, Collections.emptyList());

        String[] headers = {
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                "\"Single Select Coded\""
        };
        assertDoesNotThrow(() -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.UPLOAD_VISIT_DETAILS);
        });
    }
}
