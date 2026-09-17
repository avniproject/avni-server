package org.avni.server.web.response;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.Concept;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.User;
import org.avni.server.service.ConceptService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * #1053 - a system reading approvals through /api/approvalStatuses gets the answers captured on the
 * approval or rejection form alongside the status, so the reason a record was rejected is readable
 * outside Avni rather than being locked in the jsonb column.
 *
 * The answers are rendered through Response.putObservations - the same helper /api/subjects,
 * /api/encounters and /api/enrolments use - so an integration reads a flat {question name: resolved
 * answer} map here and there, with coded answers resolved to concept names rather than left as UUIDs.
 */
public class EntityApprovalStatusResponseUnitTest {
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptService conceptService;

    private User user;

    @Before
    public void setup() {
        initMocks(this);
        user = new User();
        user.setUsername("approver@example");
    }

    private EntityApprovalStatus rejectionWith(ObservationCollection observations) {
        ApprovalStatus approvalStatus = new ApprovalStatus();
        approvalStatus.setStatus(ApprovalStatus.Status.Rejected);

        EntityApprovalStatus entityApprovalStatus = new EntityApprovalStatus();
        entityApprovalStatus.setEntityType(EntityApprovalStatus.EntityType.Subject);
        entityApprovalStatus.setEntityTypeUuid("subject-type-uuid");
        entityApprovalStatus.setApprovalStatus(approvalStatus);
        entityApprovalStatus.setApprovalStatusComment("Address did not match");
        entityApprovalStatus.setStatusDateTime(new DateTime());
        entityApprovalStatus.setObservations(observations);
        entityApprovalStatus.setCreatedBy(user);
        entityApprovalStatus.setLastModifiedBy(user);
        return entityApprovalStatus;
    }

    /**
     * Stubs a question concept the way Response.mapObservations reaches it: the observation key is the
     * question's UUID, looked up on the repository, and the stored value is resolved by ConceptService.
     */
    private Concept question(String conceptUuid, String name, Object storedValue, Object resolvedValue) {
        Concept concept = new Concept();
        concept.setUuid(conceptUuid);
        concept.setName(name);
        when(conceptRepository.findByUuid(conceptUuid)).thenReturn(concept);
        when(conceptService.getObservationValue(concept, storedValue)).thenReturn(resolvedValue);
        return concept;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> observationsOf(EntityApprovalStatusResponse response) {
        return (Map<String, Object>) response.get("observations");
    }

    @Test
    public void theAnswersOnADecisionAreReturnedAlongsideItsStatus() {
        ObservationCollection observations = new ObservationCollection();
        observations.put("rejection-reason-uuid", "wrong-address-answer-uuid");
        observations.put("rejection-note-uuid", "Door number missing");
        question("rejection-reason-uuid", "Rejection reason", "wrong-address-answer-uuid", "Wrong address");
        question("rejection-note-uuid", "Rejection note", "Door number missing", "Door number missing");

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", conceptRepository, conceptService);

        LinkedHashMap<String, Object> expected = new LinkedHashMap<>();
        expected.put("Rejection reason", "Wrong address");
        expected.put("Rejection note", "Door number missing");
        assertEquals("the answers must be keyed by question name with the answer resolved, as on /api/subjects",
                expected, observationsOf(response));
    }

    /**
     * The point of the story: an integration reading a rejection reason gets the answer's name, not the
     * answer concept's UUID. Renders through ConceptService.getObservationValue, which resolves coded
     * answers - ObservationService.constructObservations, which this response used to call, does not.
     */
    @Test
    public void aCodedAnswerIsResolvedToItsNameRatherThanItsUuid() {
        ObservationCollection observations = new ObservationCollection();
        observations.put("rejection-reason-uuid", "wrong-address-answer-uuid");
        question("rejection-reason-uuid", "Rejection reason", "wrong-address-answer-uuid", "Wrong address");

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", conceptRepository, conceptService);

        assertEquals("Wrong address", observationsOf(response).get("Rejection reason"));
    }

    /**
     * The story asks for the answers to sit immediately after the comment they replace. The response is
     * a LinkedHashMap, so this is the serialised field order an integration consumer sees.
     */
    @Test
    public void theAnswersFollowTheApprovalStatusComment() {
        ObservationCollection observations = new ObservationCollection();
        observations.put("rejection-reason-uuid", "wrong-address-answer-uuid");
        question("rejection-reason-uuid", "Rejection reason", "wrong-address-answer-uuid", "Wrong address");

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", conceptRepository, conceptService);

        List<String> keys = new ArrayList<>(response.keySet());
        assertEquals("observations must directly follow the comment it replaces: " + keys,
                keys.indexOf("Approval status comment") + 1, keys.indexOf("observations"));
        assertTrue("the existing keys must all survive: " + keys,
                keys.containsAll(Arrays.asList("Entity ID", "Entity type", "Entity type ID",
                        "Approval status", "Approval status comment", "Status date time", "audit")));
    }

    /**
     * AC #2 - every decision taken before an approval form was attached, and every decision an
     * organisation with no form will ever take, has a SQL NULL observations column. Those come back with
     * an explicit null rather than an empty map, so a consumer can tell "no form was ever involved" from
     * "the form was opened and every question left blank". mapObservations would flatten both to {}.
     */
    @Test
    public void aDecisionWithNoAnswersReturnsNullAndNeverReachesTheConceptLookup() {
        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(null), "entity-uuid", conceptRepository, conceptService);

        assertTrue("the key should still be present, with an explicit null",
                response.containsKey("observations"));
        assertNull("a decision with no answers must not invent an empty map", response.get("observations"));
        verify(conceptRepository, never()).findByUuid(any());
    }

    @Test
    public void aDecisionWithAnEmptyAnswerCollectionIsRenderedAsAnEmptyMap() {
        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(new ObservationCollection()), "entity-uuid", conceptRepository, conceptService);

        assertEquals(Collections.emptyMap(), observationsOf(response));
    }
}
