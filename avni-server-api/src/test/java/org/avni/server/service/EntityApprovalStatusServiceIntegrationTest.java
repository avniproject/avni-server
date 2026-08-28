package org.avni.server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.EntityApprovalStatusRequest;
import org.avni.server.web.request.ObservationRequest;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Answers captured on an approval or rejection form are stored on the entity_approval_status row for
 * that decision (#1051), so a later decision cannot overwrite an earlier one's reasons.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class EntityApprovalStatusServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private EntityApprovalStatusService entityApprovalStatusService;
    @Autowired
    private ApprovalStatusRepository approvalStatusRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    private Individual subject;
    private ApprovalStatus pending;
    private ApprovalStatus rejected;
    private Concept rejectionReason;
    private Concept answerWrongAddress;
    private Concept rejectionNote;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testDataSetupService.setupOrganisation();
        SubjectType subjectType = new SubjectTypeBuilder().setType(Subject.Individual).setName("ST1").build();
        testSubjectTypeService.createWithDefaults(subjectType);
        subject = new SubjectBuilder().withSubjectType(subjectType).withFirstName("foo")
                .withRegistrationDate(new LocalDate()).build();
        testSubjectService.save(subject);

        pending = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Pending);
        rejected = approvalStatusRepository.findByStatus(ApprovalStatus.Status.Rejected);

        rejectionReason = testConceptService.createCodedConcept("Rejection reason", "Wrong address", "Duplicate");
        answerWrongAddress = rejectionReason.findAnswerConcept("Wrong address");
        assertNotNull("The answer concept must be seeded before any decision is posted - "
                + "ObservationService.createObservations fails loudly on an unknown concept", answerWrongAddress);
        rejectionNote = testConceptService.createConcept("Rejection note", ConceptDataType.Text);
    }

    private ObservationRequest observation(String conceptUuid, Object value) {
        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(conceptUuid);
        observationRequest.setValue(value);
        return observationRequest;
    }

    private String saveDecision(ApprovalStatus approvalStatus, DateTime statusDateTime,
                                List<ObservationRequest> observations) {
        return saveDecision(UUID.randomUUID().toString(), approvalStatus, statusDateTime, observations);
    }

    private String saveDecision(String uuid, ApprovalStatus approvalStatus, DateTime statusDateTime,
                                List<ObservationRequest> observations) {
        EntityApprovalStatusRequest request = new EntityApprovalStatusRequest();
        request.setUuid(uuid);
        request.setEntityUuid(subject.getUuid());
        request.setEntityType(EntityApprovalStatus.EntityType.Subject.name());
        request.setApprovalStatusUuid(approvalStatus.getUuid());
        request.setStatusDateTime(statusDateTime);
        request.setAutoApproved(false);
        request.setObservations(observations);
        entityApprovalStatusService.save(request);
        entityManager.flush();
        return uuid;
    }

    private String storedObservations(String uuid) {
        return jdbcTemplate.queryForObject(
                "select observations::text from entity_approval_status where uuid = ?",
                String.class, uuid);
    }

    @Test
    public void answersSentWithADecisionAreSavedAgainstIt() {
        String uuid = saveDecision(rejected, new DateTime(), Arrays.asList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid()),
                observation(rejectionNote.getUuid(), "House number did not match")));

        String observations = storedObservations(uuid);
        assertNotNull("The decision should have stored its answers", observations);
        assertTrue("Answers are keyed by concept UUID: " + observations,
                observations.contains(rejectionReason.getUuid()));
        assertTrue("The free-text answer should be stored: " + observations,
                observations.contains("House number did not match"));
    }

    /**
     * A coded answer is stored as the answer concept's UUID, exactly as it is on a visit form, so
     * reports read approval answers with the same query they use for every other observation.
     *
     * What this pins down is that nothing on the approval path renames, resolves or otherwise mangles
     * the value between the request and the column. It is NOT server-side enforcement: createObservations
     * stores the value verbatim (ObservationService.java:71) and never maps an answer name to a UUID
     * nor checks the answer belongs to the concept - a client that sends "Wrong address" would store
     * that string. The visit-form path behaves identically, so this is a client contract on both, and
     * "stored the same way it is on a visit form" is satisfied by sharing the conversion. Enforcing it
     * server-side would mean validating against the Approval/Rejection form mapping, which is
     * avniproject/avni-server#1052, not this story.
     */
    @Test
    public void aCodedAnswerIsStoredAsTheAnswerConceptUuid() {
        String uuid = saveDecision(rejected, new DateTime(), Arrays.asList(
                observation(rejectionReason.getUuid(), answerWrongAddress.getUuid())));

        String observations = storedObservations(uuid);
        assertTrue("The answer concept UUID should be stored: " + observations,
                observations.contains(answerWrongAddress.getUuid()));
        assertTrue("The answer's name must not be stored in place of its UUID: " + observations,
                !observations.contains("Wrong address"));
    }

    /**
     * The path every client released before the approval/rejection form takes. Its stored shape must
     * not change: SQL NULL, never {}.
     */
    @Test
    public void aDecisionWithNoAnswersStoresSqlNull() {
        String uuid = saveDecision(rejected, new DateTime(), null);

        assertNull("A decision with no observations must store SQL NULL, not {}", storedObservations(uuid));
    }

    @Test
    public void aDecisionWithAnEmptyAnswerListStoresSqlNull() {
        String uuid = saveDecision(rejected, new DateTime(), Collections.emptyList());

        assertNull("An empty answer list must store SQL NULL, not {}", storedObservations(uuid));
    }

    /**
     * save() is an upsert on the request uuid, and the row it finds is JPA-managed inside the
     * controller's transaction, so an unguarded setter would flush by dirty-checking. A client that
     * re-posts a decision without an observations key - a sync retry, a comment correction, or any
     * client that predates the form during a mixed-version rollout - must not erase answers that are
     * already stored. Absent key means "nothing to say about the answers"; an empty list clears them.
     */
    @Test
    public void reSavingADecisionWithoutAnswersDoesNotEraseThem() {
        String uuid = saveDecision(rejected, new DateTime().minusDays(1), Arrays.asList(
                observation(rejectionNote.getUuid(), "Address looks wrong")));
        assertNotNull(storedObservations(uuid));

        saveDecision(uuid, rejected, new DateTime(), null);

        String observations = storedObservations(uuid);
        assertNotNull("Re-posting without an observations key must not erase stored answers", observations);
        assertTrue("The original answers must survive: " + observations,
                observations.contains("Address looks wrong"));
    }

    @Test
    public void reSavingADecisionWithAnEmptyAnswerListClearsTheAnswers() {
        String uuid = saveDecision(rejected, new DateTime().minusDays(1), Arrays.asList(
                observation(rejectionNote.getUuid(), "Address looks wrong")));
        assertNotNull(storedObservations(uuid));

        saveDecision(uuid, rejected, new DateTime(), Collections.emptyList());

        assertNull("An explicit empty list means clear the answers", storedObservations(uuid));
    }

    /**
     * Two rejections of the same record keep their own answers and their own dates.
     *
     * The intermediate Pending is not decoration. EntityApprovalStatusRepository.saveEAS silently
     * drops a row whose status matches the entity's latest non-voided status, so a back-to-back
     * Rejected/Rejected never produces two rows. The cycle below is the one the product actually
     * produces: reject, the field user edits the record and it returns to Pending, reject again.
     * That guard is pre-existing behaviour and is out of scope for #1051.
     */
    @Test
    public void rejectingTheSameRecordTwiceKeepsBothSetsOfAnswers() {
        DateTime firstRejection = new DateTime().minusDays(3);
        String firstUuid = saveDecision(rejected, firstRejection, Arrays.asList(
                observation(rejectionNote.getUuid(), "Address looks wrong")));

        saveDecision(pending, new DateTime().minusDays(2), null);

        DateTime secondRejection = new DateTime().minusDays(1);
        String secondUuid = saveDecision(rejected, secondRejection, Arrays.asList(
                observation(rejectionNote.getUuid(), "Still wrong after the edit")));

        List<Map<String, Object>> rejections = jdbcTemplate.queryForList(
                "select uuid, status_date_time, observations::text as observations " +
                        "from entity_approval_status " +
                        "where entity_id = ? and entity_type = 'Subject' and is_voided = false " +
                        "and approval_status_id = ? " +
                        "order by status_date_time",
                subject.getId(), rejected.getId());

        assertEquals("Both rejections should be kept as separate rows", 2, rejections.size());
        assertEquals(firstUuid, rejections.get(0).get("uuid"));
        assertEquals(secondUuid, rejections.get(1).get("uuid"));
        assertTrue("The earlier rejection must keep its own answers: " + rejections.get(0),
                ((String) rejections.get(0).get("observations")).contains("Address looks wrong"));
        assertTrue("The earlier rejection must not have been overwritten: " + rejections.get(0),
                !((String) rejections.get(0).get("observations")).contains("Still wrong after the edit"));
        assertTrue("The later rejection keeps its own answers: " + rejections.get(1),
                ((String) rejections.get(1).get("observations")).contains("Still wrong after the edit"));
    }
}
