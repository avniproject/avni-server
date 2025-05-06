package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.Concept;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EncounterHeadersCreatorIntegrationTest extends AbstractControllerIntegrationTest {
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

    private SubjectType subjectType;
    private EncounterType encounterType;
    private FormMapping formMapping;

    @Before
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());

        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build()
        );

        encounterType = new EncounterTypeBuilder()
                .withName("Test Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();
        String formName = "Test Encounter Form " + java.util.UUID.randomUUID();
        formMapping = testFormService.createEncounterForm(subjectType, encounterType, formName, Collections.emptyList(), Collections.emptyList());
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
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.SCHEDULE_VISIT, new ArrayList<>());
        });
        assertTrue(exception.getMessage().contains("Mandatory columns are missing"));
    }

    @Test
    public void testHeaderValidation_allowsOptionalHeaders() {
        Concept codedConcept = testConceptService.createCodedConcept("Multi Select Coded", "SSC Answer 1", "SSC Answer 2");
        java.util.List<String> multiSelectConcept = java.util.Collections.singletonList(codedConcept.getName());
        encounterType = new EncounterTypeBuilder()
                .withName("Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();
        String formName = "Test Encounter Form " + java.util.UUID.randomUUID();
        formMapping = testFormService.createEncounterForm(subjectType, encounterType, formName, Collections.emptyList(), multiSelectConcept);

        String[] headers = {
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                "\"Multi Select Coded\""
        };
        assertDoesNotThrow(() -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.UPLOAD_VISIT_DETAILS, new ArrayList<>());
        });
    }
}
