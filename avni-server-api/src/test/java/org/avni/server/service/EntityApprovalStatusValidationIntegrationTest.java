package org.avni.server.service;

import org.avni.server.application.FormType;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ApprovalStatusRepository;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.EntityApprovalStatusRequest;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.domain.ValidationException;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * #1051 review (ombhardwajj, 1 Sep 2026) - the approval path was the only observation write in the
 * codebase that stored answers without validating them first. Every other one validates against the
 * form mapping (IndividualController, EncounterController, ProgramEnrolmentService,
 * ProgramEncounterService) and avni.enhancedValidation.enabled defaults to true.
 *
 * The consequence was that a coded answer outside the concept's answer set, or a string in a Numeric
 * question, was accepted here and surfaced later as bad jsonb in reporting, where every other entity
 * would have returned a validation error at save time.
 *
 * Two boundaries are deliberate and tested below. A decision carrying no answers is never validated and
 * needs no form attached - that is every client released before this feature, and its behaviour must not
 * change. A decision carrying answers with no decision form attached is refused rather than stored
 * unvalidated, which is safe to introduce now because no released client sends answers at all.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EntityApprovalStatusValidationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private EntityApprovalStatusService entityApprovalStatusService;
    @Autowired
    private ApprovalStatusRepository approvalStatusRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObservationService observationService;
    @Autowired
    private OrganisationConfigService organisationConfigService;

    private SubjectType subjectType;
    private Individual subject;
    private ApprovalStatus pending;
    private ApprovalStatus approved;
    private ApprovalStatus rejected;
    private Concept rejectionReason;
    private Concept answerWrongAddress;
    private Concept amountApproved;
    private Concept conceptNotOnAnyForm;

    /**
     * The test profile ships AVNI_ENHANCED_VALIDATION=false; production defaults to true. @TestPropertySource
     * would be the obvious way to switch it on, but it forces a second Spring context and the application's
     * Ehcache CacheManager cannot be built twice in one JVM - every context-sharing test in the run then
     * fails to load. Flipping the field on the shared bean avoids that, and tearDown puts it back so the
     * cached context is handed on unchanged.
     */
    private void setEnhancedValidation(boolean enabled) {
        // The local is load-bearing: passing getUltimateTargetObject(..) straight in makes Java infer the
        // setField(Class, ..) overload and fail with a ClassCastException at runtime.
        ObservationService target = AopTestUtils.getUltimateTargetObject(observationService);
        ReflectionTestUtils.setField(target, "enhancedValidationEnabled", enabled);
    }

    @After
    public void tearDown() {
        setEnhancedValidation(false);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setEnhancedValidation(true);
        testDataSetupService.setupOrganisation();
        // Two switches gate a validation error, not one. avni.enhancedValidation.enabled decides whether the
        // check runs at all; the organisation's failOnValidationError decides whether a failed check actually
        // refuses the save or is merely reported to Bugsnag. Both are needed to observe a refusal, and this is
        // exactly how every other entity behaves - an organisation that has not opted in keeps storing.
        organisationConfigService.updateSettings(
                OrganisationConfigSettingKey.failOnValidationError.name(), true);
        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setType(Subject.Individual).setName("ST1051").build());
        subject = new SubjectBuilder().withSubjectType(subjectType).withFirstName("foo")
                .withRegistrationDate(new LocalDate()).build();
        testSubjectService.save(subject);

        pending = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Pending);
        approved = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Approved);
        rejected = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Rejected);

        rejectionReason = testConceptService.createCodedConcept("Rejection reason", "Wrong address", "Duplicate");
        answerWrongAddress = rejectionReason.findAnswerConcept("Wrong address");
        amountApproved = testConceptService.createConcept("Amount approved", ConceptDataType.Numeric);
        conceptNotOnAnyForm = testConceptService.createConcept("Not on any form", ConceptDataType.Text);
    }

    private void attachRejectionForm() {
        testFormService.createDecisionForm(subjectType, FormType.Rejection, "Rejection form 1051",
                Arrays.asList("Rejection reason", "Amount approved"), Collections.emptyList());
    }

    private void attachApprovalForm() {
        testFormService.createDecisionForm(subjectType, FormType.Approval, "Approval form 1051",
                Collections.singletonList("Amount approved"), Collections.emptyList());
    }

    private ObservationRequest observation(String conceptUuid, Object value) {
        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(conceptUuid);
        observationRequest.setValue(value);
        return observationRequest;
    }

    private String saveDecision(ApprovalStatus approvalStatus, List<ObservationRequest> observations) throws ValidationException {
        String uuid = UUID.randomUUID().toString();
        EntityApprovalStatusRequest request = new EntityApprovalStatusRequest();
        request.setUuid(uuid);
        request.setEntityUuid(subject.getUuid());
        request.setEntityType(EntityApprovalStatus.EntityType.Subject.name());
        request.setApprovalStatusUuid(approvalStatus.getUuid());
        request.setStatusDateTime(new DateTime());
        request.setAutoApproved(false);
        request.setObservations(observations);
        entityApprovalStatusService.save(request);
        return uuid;
    }

    private String storedObservations(String uuid) {
        return jdbcTemplate.queryForObject(
                "select observations::text from entity_approval_status where uuid = ?", String.class, uuid);
    }

    private void assertRefused(ApprovalStatus status, List<ObservationRequest> observations, String expectedInMessage) {
        // save() declares the checked ValidationException; the try below is what catches it.
        try {
            saveDecision(status, observations);
            fail("Expected the decision to be refused");
        } catch (ValidationException e) {
            assertTrue("The message must say what is wrong, got: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains(expectedInMessage.toLowerCase()));
        }
    }

    // The two cases Om named

    @Test
    public void aCodedAnswerOutsideTheConceptsAnswerSetIsRefused() throws ValidationException {
        attachRejectionForm();

        assertRefused(rejected, Collections.singletonList(
                observation(rejectionReason.getUuid(), conceptNotOnAnyForm.getUuid())), "answer");
    }

    @Test
    public void aStringSentIntoANumericQuestionIsRefused() throws ValidationException {
        attachRejectionForm();

        assertRefused(rejected, Collections.singletonList(
                observation(amountApproved.getUuid(), "not a number")), "numeric");
    }

    /**
     * A question that is not on the attached form at all. This is what catches a client sending answers
     * from the wrong form, or from a form element that has since been detached.
     *
     * Asserted as "refused", not "refused with this message", because of a pre-existing defect in shared
     * validation code that this test happens to walk into. EnhancedValidationService runs
     * checkForInvalidConceptUUIDAndNames - which correctly records "Invalid concept uuids/names" - and
     * then runs validateConceptValuesAreOfRequiredType before handleValidationFailure ever gets to throw
     * it. For a concept absent from the form, formElements.get(uuid) is null, and validateTextValue
     * dereferences it at EnhancedValidationService:213 without a null check. So the caller sees an NPE
     * instead of the message that was already prepared for them.
     *
     * That is not specific to approvals - a Text answer for an off-form concept does the same on a
     * subject or an encounter - so it is not this change's to fix, and is worth its own issue. What
     * matters here is the property Om asked for: the answers do not get stored.
     */
    @Test
    public void aQuestionThatIsNotOnTheAttachedFormIsRefused() {
        attachRejectionForm();

        try {
            saveDecision(rejected, Collections.singletonList(
                    observation(conceptNotOnAnyForm.getUuid(), "anything")));
            fail("A question that is not on the attached form must not be stored");
        } catch (ValidationException | RuntimeException expected) {
            // Refused. See the javadoc for why the exception type is not pinned down.
        }
    }

    @Test
    public void validAnswersOnTheAttachedFormAreStored() throws ValidationException {
        attachRejectionForm();

        String uuid = saveDecision(rejected, Arrays.asList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid()),
                observation(amountApproved.getUuid(), 42)));

        String observations = storedObservations(uuid);
        assertNotNull("valid answers must still be stored", observations);
        assertTrue(observations.contains(answerWrongAddress.getUuid()));
    }

    // The boundary that must not change for existing clients

    /**
     * Every client released before the approval form sends no observations key. Such a decision is never
     * validated and needs no form attached - note there is no attachRejectionForm() call here.
     */
    @Test
    public void aDecisionWithNoAnswersIsAcceptedWithNoFormAttached() throws ValidationException {
        String uuid = saveDecision(rejected, null);

        assertNull("stored shape is unchanged for a decision that carries no answers",
                storedObservations(uuid));
    }

    @Test
    public void aDecisionWithAnEmptyAnswerListIsAcceptedWithNoFormAttached() throws ValidationException {
        String uuid = saveDecision(rejected, Collections.emptyList());

        assertNull(storedObservations(uuid));
    }

    // The new boundary

    @Test
    public void answersSentWithNoDecisionFormAttachedAreRefused() throws ValidationException {
        assertRefused(rejected, Collections.singletonList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid())), "no Rejection form");
    }

    /**
     * Pending is not a decision anyone fills a form for, so there is no form type to validate against.
     * Answers arriving on one are a client error.
     */
    @Test
    public void answersSentOnAPendingDecisionAreRefused() throws ValidationException {
        attachRejectionForm();

        assertRefused(pending, Collections.singletonList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid())), "Pending");
    }

    /**
     * The discriminating case for choosing the form by status. "Rejection reason" is on the Rejection
     * form and not on the Approval form, so an approval carrying it must be refused - if the lookup
     * ignored the status and picked whichever decision form existed, this would pass.
     */
    @Test
    public void anApprovalIsValidatedAgainstTheApprovalFormNotTheRejectionForm() throws ValidationException {
        attachRejectionForm();
        attachApprovalForm();

        assertRefused(approved, Collections.singletonList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid())), "invalid concept");

        String uuid = saveDecision(approved, Collections.singletonList(observation(amountApproved.getUuid(), 7)));
        assertNotNull("a question on the approval form is accepted", storedObservations(uuid));
    }

    @Test
    public void aRejectionIsRefusedWhenOnlyAnApprovalFormIsAttached() throws ValidationException {
        attachApprovalForm();

        assertRefused(rejected, Collections.singletonList(
                observation(amountApproved.getUuid(), 7)), "no Rejection form");
    }
}
