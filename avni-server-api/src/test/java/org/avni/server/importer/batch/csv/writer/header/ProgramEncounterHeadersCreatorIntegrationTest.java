package org.avni.server.importer.batch.csv.writer.header;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
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

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ProgramEncounterHeadersCreatorIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private EncounterHeadersCreator encounterHeadersCreator;

    @Autowired
    private FormMappingRepository formMappingRepository;

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;

    @Autowired
    private TestDataSetupService testDataSetupService;

    @Autowired
    private TestFormService testFormService;

    @Autowired
    private TestConceptService testConceptService;

    private FormMapping formMapping;

    @Before
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser());

        var subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build()
        );

        var form = new TestFormBuilder()
                .withFormType(FormType.ProgramEncounter)
                .build();

        var encounterType = new EncounterTypeBuilder()
                .withName("Test Program Encounter Type")
                .withUuid(java.util.UUID.randomUUID().toString())
                .build();

        formMapping = new FormMappingBuilder()
                .withForm(form)
                .withSubjectType(subjectType)
                .withEncounterType(encounterType)
                .build();
        formMappingRepository.save(formMapping);
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
        String[] headers = {
                EncounterHeadersCreator.ID,
                EncounterHeadersCreator.VISIT_DATE,
                EncounterHeadersCreator.PROGRAM_ENROLMENT_ID,
                EncounterHeadersCreator.ENCOUNTER_TYPE_HEADER
                // Add a valid concept field header if needed, e.g. "Concept Field Name"
        };
        assertDoesNotThrow(() -> {
            TxnDataHeaderValidator.validateHeaders(headers, formMapping, encounterHeadersCreator, EncounterUploadMode.UPLOAD_VISIT_DETAILS);
        });
    }
}
