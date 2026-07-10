package org.avni.server.importer.batch.csv.writer.header;

import org.springframework.transaction.annotation.Transactional;
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
import java.util.List;

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
    public void testBasicHeaderGeneration_ScheduledVisit() {
        String[] headers = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.SCHEDULE_VISIT);
        System.out.println("Generated headers: " + Arrays.toString(headers));
        assertNotNull(headers);
        assertTrue(headers.length > 0);
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.ID));
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.EARLIEST_VISIT_DATE));
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.MAX_VISIT_DATE));

        String[] allDescriptions = encounterHeadersCreator.getAllDescriptions(formMapping, EncounterUploadMode.SCHEDULE_VISIT);
        assertEquals("Mandatory. Test Encounter Type.", allDescriptions[1]);
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
    public void testAllowsOptionalHeaders() {
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
                "Multi Select Coded"
        };
        assertDoesNotThrow(() -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.UPLOAD_VISIT_DETAILS, new ArrayList<>());
        });
    }

    @Test
    public void testDecisionConceptHeader_uploadVisitDetails_andAbsentFromScheduleVisit() {
        Concept decisionConcept = testConceptService.createCodedConcept("Weight for Age status", "Normal", "Moderate", "Severe");
        testFormService.addDecisionConcepts(formMapping.getForm().getId(), decisionConcept);

        String[] visitHeaders = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.UPLOAD_VISIT_DETAILS);
        assertTrue(Arrays.asList(visitHeaders).contains("\"Weight for Age status\""),
                "Upload visit details should include the decision concept column");

        String[] scheduleHeaders = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.SCHEDULE_VISIT);
        assertFalse(Arrays.asList(scheduleHeaders).contains("\"Weight for Age status\""),
                "Schedule visit should not include any concept columns");
    }

    @Test
    public void testDecisionConceptHeader_uploadCancelledVisit() {
        Concept visitDecision = testConceptService.createCodedConcept("Weight for Age status", "Normal", "Moderate", "Severe");
        testFormService.addDecisionConcepts(formMapping.getForm().getId(), visitDecision);

        Concept cancelDecision = testConceptService.createCodedConcept("Cancellation Outcome status", "Refused", "Not Available");
        FormMapping cancellationFormMapping = testFormService.createGeneralEncounterCancellationForm(
                subjectType, encounterType, "Cancellation Form " + java.util.UUID.randomUUID(),
                Collections.emptyList(), Collections.emptyList());
        testFormService.addDecisionConcepts(cancellationFormMapping.getForm().getId(), cancelDecision);

        String[] headers = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.UPLOAD_CANCELLED_VISIT);
        assertTrue(Arrays.asList(headers).contains("\"Cancellation Outcome status\""),
                "Upload cancelled visit should include the cancellation form's decision concept column");
        assertFalse(Arrays.asList(headers).contains("\"Weight for Age status\""),
                "Upload cancelled visit should not include the visit form's decision concept column");
    }

    @Test
    public void testUploadCancelledVisit_withoutCancellationForm_doesNotFail() {
        String[] headers = encounterHeadersCreator.getAllHeaders(formMapping, EncounterUploadMode.UPLOAD_CANCELLED_VISIT);
        assertNotNull(headers);
        assertTrue(Arrays.asList(headers).contains(EncounterHeadersCreator.CANCEL_DATE));
    }

    @Test
    public void testAllowsHeadersWithQuotesInBetween() {
        Concept codedConcept = testConceptService.createCodedConcept("\"Multi \"Select\" Coded\"", "SSC Answer 1", "SSC Answer 2");
        Concept codedConcept2 = testConceptService.createCodedConcept("\"test\"", "test Answer 1", "test Answer 2");
        java.util.List<String> multiSelectConcepts = List.of(codedConcept.getName(), codedConcept2.getName());
        encounterType = new EncounterTypeBuilder()
                .withName("Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();
        String formName = "Test Encounter Form " + java.util.UUID.randomUUID();
        formMapping = testFormService.createEncounterForm(subjectType, encounterType, formName, Collections.emptyList(), multiSelectConcepts);

        String[] headers = {
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.SUBJECT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE,
                "\"Multi \"Select\" Coded\"",
                "\"test\""
        };
        assertDoesNotThrow(() -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.UPLOAD_VISIT_DETAILS, new ArrayList<>());
        });
    }
}
