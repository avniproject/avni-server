package org.avni.server.web.api;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ApprovalStatusRepository;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.EntityApprovalStatusService;
import org.avni.server.application.FormType;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.EntityApprovalStatusRequest;
import org.avni.server.web.request.ObservationContract;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * #1053 - AC #1 and #2, end to end through the controller rather than the response class alone.
 *
 * This exercises what the unit test cannot: that the concepts actually resolve against the database, and
 * that the approve-privilege check the controller runs before serialisation is satisfied.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EntityApprovalStatusApiControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private EntityApprovalStatusService entityApprovalStatusService;
    @Autowired
    private ApprovalStatusRepository approvalStatusRepository;
    @Autowired
    private EntityApprovalStatusApiController entityApprovalStatusApiController;

    private SubjectType subjectType;
    private Individual subject;
    private ApprovalStatus rejected;
    private Concept rejectionReason;
    private Concept answerWrongAddress;
    private Concept rejectionNote;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        subjectType = new SubjectTypeBuilder().setType(Subject.Individual).setName("ST1").build();
        testSubjectTypeService.createWithDefaults(subjectType);

        // checkApprovePrivilegeOnEntityApprovalStatuses runs in the controller body and throws for any
        // entity type in the page the caller cannot approve, before any serialisation happens.
        testGroupService.giveApproveSubjectPrivilegeTo(organisationData.getGroup(), subjectType);

        subject = new SubjectBuilder().withSubjectType(subjectType).withFirstName("foo")
                .withRegistrationDate(new LocalDate()).build();
        testSubjectService.save(subject);

        rejected = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Rejected);
        rejectionReason = testConceptService.createCodedConcept("Rejection reason", "Wrong address", "Duplicate");
        answerWrongAddress = rejectionReason.findAnswerConcept("Wrong address");
        rejectionNote = testConceptService.createConcept("Rejection note", ConceptDataType.Text);

        // Answers on a decision are validated against the attached decision form (#1051 review), so the
        // questions read back below have to be on one.
        testFormService.createDecisionForm(subjectType, FormType.Rejection, "Rejection form 1053",
                java.util.Arrays.asList("Rejection reason", "Rejection note"), java.util.Collections.emptyList());
    }

    private ObservationRequest observation(String conceptUuid, Object value) {
        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(conceptUuid);
        observationRequest.setValue(value);
        return observationRequest;
    }

    private void saveDecision(List<ObservationRequest> observations) throws ValidationException {
        EntityApprovalStatusRequest request = new EntityApprovalStatusRequest();
        request.setUuid(UUID.randomUUID().toString());
        request.setEntityUuid(subject.getUuid());
        request.setEntityType(EntityApprovalStatus.EntityType.Subject.name());
        request.setApprovalStatusUuid(rejected.getUuid());
        request.setStatusDateTime(new DateTime());
        request.setAutoApproved(false);
        request.setObservations(observations);
        entityApprovalStatusService.save(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> singleResponseEntry() {
        // `now` is passed explicitly rather than left to default. findEntityApprovalStatuses falls back to
        // new DateTime() evaluated when the query runs, and filters lastModifiedBetween(REALLY_OLD_DATE, now)
        // - so a decision saved in the same millisecond as the read sits on the boundary and intermittently
        // drops out. Real callers of this sync endpoint always pass `now`; leaving it null made this test
        // flaky roughly one run in two.
        ResponsePage page = entityApprovalStatusApiController.getEntityApprovalStatuses(
                null, new DateTime().plusMinutes(1), null, null, PageRequest.of(0, 10));
        assertEquals("exactly one decision was saved", 1, page.getContent().size());
        return (Map<String, Object>) page.getContent().get(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void theAnswersOnARejectionAreReadableThroughTheApi() throws ValidationException {
        saveDecision(Arrays.asList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid()),
                observation(rejectionNote.getUuid(), "Door number did not match")));

        Map<String, Object> entry = singleResponseEntry();

        List<ObservationContract> observations = (List<ObservationContract>) entry.get("observations");
        assertNotNull("a rejection carrying answers must return them", observations);
        List<String> questions = observations.stream()
                .map(o -> o.getConcept().getName()).collect(Collectors.toList());
        assertTrue("both questions should come back, named rather than as concept UUIDs: " + questions,
                questions.containsAll(Arrays.asList("Rejection reason", "Rejection note")));

        Object codedValue = observations.stream()
                .filter(o -> o.getConcept().getName().equals("Rejection reason"))
                .findFirst().get().getValue();
        assertEquals("the coded answer is returned as the answer concept UUID, as it is stored",
                answerWrongAddress.getUuid(), codedValue);
    }

    /**
     * AC #2. This is the shape every organisation that never attaches an approval form keeps seeing, and
     * the shape of every decision recorded before one was attached.
     */
    @Test
    public void aRejectionWithNoAnswersIsReturnedWithoutError() throws ValidationException {
        saveDecision(null);

        Map<String, Object> entry = singleResponseEntry();

        assertTrue("the key is present", entry.containsKey("observations"));
        assertNull("a decision with no answers returns an explicit null", entry.get("observations"));
        assertEquals("the rest of the decision is unaffected",
                ApprovalStatus.Status.Rejected, entry.get("Approval status"));
        assertEquals(subject.getUuid(), entry.get("Entity ID"));
    }

    /**
     * The response is a LinkedHashMap and the keys contain spaces, so this asserts on position rather
     * than on a serialised string.
     */
    @Test
    public void theExistingKeysAndTheirOrderAreUnchanged() throws ValidationException {
        saveDecision(null);

        List<String> keys = new java.util.ArrayList<>(((LinkedHashMap<String, Object>) singleResponseEntry()).keySet());

        assertEquals(Arrays.asList("Entity ID", "Entity type", "Entity type ID", "Approval status",
                        "Approval status comment", "observations", "Status date time", "audit"),
                keys);
    }
}
