package org.avni.server.service;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * #1053 AC #3 and #4 - approving records in bulk from a spreadsheet must keep working once an approval
 * form is attached, and must behave exactly as it does today when none is.
 *
 * Read this before trusting the story's wording. AC #3 says to verify the CSV import path writes null
 * observations, but that path does not reach a writer: EntityApprovalStatusWriter has been unreachable
 * since #563 created it - nothing autowires it and nothing calls saveStatus - and no CSV writer creates
 * approval rows at all (SubjectWriter calls individualService.save directly, never IndividualController).
 * A bulk import therefore writes no approval row whatsoever, rather than one with null observations.
 *
 * So this pins the contract one level down, at EntityApprovalStatusService.createStatus - the method the
 * CSV path would use and the method every app-side registration and encounter already uses. That is where
 * the actual regression risk sits: createStatus takes no observations argument, so attaching an approval
 * form must not start populating them, and must not stop the row being written either.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EntityApprovalStatusBulkApprovalIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private EntityApprovalStatusService entityApprovalStatusService;
    @Autowired
    private FormMappingRepository formMappingRepository;
    @Autowired
    private FormRepository formRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SubjectType subjectType;
    private FormMapping registrationFormMapping;
    private Individual subject;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testDataSetupService.setupOrganisation();
        subjectType = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder().setType(Subject.Individual)
                        .setUuid("st1053").setName("st1053").build()).getSubjectType();

        // Approval switched on for the subject type, which is what makes createStatus write anything at
        // all - it returns early when the mapping has approval off.
        registrationFormMapping = formMappingRepository.getRegistrationFormMapping(subjectType);
        registrationFormMapping.setEnableApproval(true);
        formMappingRepository.saveFormMapping(registrationFormMapping);

        subject = new SubjectBuilder().withSubjectType(subjectType).withFirstName("bulk")
                .withRegistrationDate(new LocalDate()).build();
        testSubjectService.save(subject);
    }

    private void attachFormOfType(FormType formType) {
        Form form = formRepository.save(new TestFormBuilder().withDefaultFieldsForNewEntity()
                .withFormType(formType).build());
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(form).withSubjectType(subjectType).withEnableApproval(true).build());
    }

    private void autoApproveOnImport() {
        entityApprovalStatusService.createStatus(EntityApprovalStatus.EntityType.Subject, subject.getId(),
                ApprovalStatus.Status.Approved, subjectType.getUuid(), registrationFormMapping);
    }

    private Map<String, Object> theOnlyApprovalRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select eas.uuid, eas.observations::text as observations, s.status " +
                        "from entity_approval_status eas " +
                        "join approval_status s on s.id = eas.approval_status_id " +
                        "where eas.entity_id = ? and eas.entity_type = 'Subject' and eas.is_voided = false",
                subject.getId());
        assertEquals("bulk approval should write exactly one approval row", 1, rows.size());
        return rows.get(0);
    }

    /**
     * AC #3. The row is written and its answers column is SQL NULL, because nobody opened a form - a
     * spreadsheet has no way to supply answers, and adding one was explicitly ruled out of scope.
     */
    @Test
    public void bulkApprovalStillWritesAnApprovedRowWhenAnApprovalFormIsAttached() {
        attachFormOfType(FormType.Approval);

        autoApproveOnImport();

        Map<String, Object> row = theOnlyApprovalRow();
        assertEquals("Approved", row.get("status"));
        assertNull("nobody opened a form, so the decision carries no answers", row.get("observations"));
    }

    /**
     * A rejection form is the commoner configuration - the story exists because DIL wanted coded rejection
     * reasons - and it must not disturb the auto-approve path either.
     */
    @Test
    public void bulkApprovalIsUnaffectedByAnAttachedRejectionForm() {
        attachFormOfType(FormType.Rejection);

        autoApproveOnImport();

        Map<String, Object> row = theOnlyApprovalRow();
        assertEquals("Approved", row.get("status"));
        assertNull("nobody opened a form, so the decision carries no answers", row.get("observations"));
    }

    /**
     * AC #4 - the baseline every organisation is on today. Identical result, which is the point: the two
     * tests above differ from this one only by the attached form.
     */
    @Test
    public void bulkApprovalWithNoFormAttachedBehavesAsItDoesToday() {
        autoApproveOnImport();

        Map<String, Object> row = theOnlyApprovalRow();
        assertEquals("Approved", row.get("status"));
        assertNull(row.get("observations"));
    }

    /**
     * The other half of AC #4: an import that is not auto-approving leaves the record awaiting a decision,
     * with no answers, exactly as before.
     */
    @Test
    public void bulkImportWithoutAutoApproveStillLeavesTheRecordPending() {
        attachFormOfType(FormType.Approval);

        entityApprovalStatusService.createStatus(EntityApprovalStatus.EntityType.Subject, subject.getId(),
                ApprovalStatus.Status.Pending, subjectType.getUuid(), registrationFormMapping);

        Map<String, Object> row = theOnlyApprovalRow();
        assertEquals("Pending", row.get("status"));
        assertNull(row.get("observations"));
    }
}
