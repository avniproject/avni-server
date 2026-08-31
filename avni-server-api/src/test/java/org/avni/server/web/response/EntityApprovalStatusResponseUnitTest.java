package org.avni.server.web.response;

import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.User;
import org.avni.server.service.ObservationService;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.ObservationContract;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
 * The answers are rendered through ObservationService.constructObservations, so the value carries its
 * concept rather than a bare UUID. That differs from the other /api/ responses, which render
 * observations as a flat {conceptName: value} map via Response.putObservations - a deliberate choice
 * on this story, not an oversight.
 */
public class EntityApprovalStatusResponseUnitTest {
    @Mock
    private ObservationService observationService;

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

    private ObservationContract observationContract(String conceptName, Object value) {
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setName(conceptName);
        ObservationContract observationContract = new ObservationContract();
        observationContract.setConcept(conceptContract);
        observationContract.setValue(value);
        return observationContract;
    }

    @Test
    public void theAnswersOnADecisionAreReturnedAlongsideItsStatus() {
        ObservationCollection observations = new ObservationCollection();
        observations.put("rejection-reason-uuid", "wrong-address-answer-uuid");

        List<ObservationContract> contracts = Arrays.asList(
                observationContract("Rejection reason", "Wrong address"),
                observationContract("Rejection note", "Door number missing"));
        when(observationService.constructObservations(observations)).thenReturn(contracts);

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", observationService);

        assertEquals("The answers should be rendered through ObservationService, not as raw jsonb",
                contracts, response.get("observations"));
    }

    /**
     * The story asks for the answers to sit immediately after the comment they replace. The response is
     * a LinkedHashMap, so this is the serialised field order an integration consumer sees.
     */
    @Test
    public void theAnswersFollowTheApprovalStatusComment() {
        ObservationCollection observations = new ObservationCollection();
        observations.put("rejection-reason-uuid", "wrong-address-answer-uuid");
        when(observationService.constructObservations(observations))
                .thenReturn(Collections.singletonList(observationContract("Rejection reason", "Wrong address")));

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", observationService);

        List<String> keys = new ArrayList<>(response.keySet());
        assertEquals("observations must directly follow the comment it replaces: " + keys,
                keys.indexOf("Approval status comment") + 1, keys.indexOf("observations"));
        assertTrue("the existing keys must all survive: " + keys,
                keys.containsAll(Arrays.asList("Entity ID", "Entity type", "Entity type ID",
                        "Approval status", "Approval status comment", "Status date time", "audit")));
    }

    /**
     * AC #2 - every decision taken before an approval form was attached, and every decision an
     * organisation with no form will ever take, has a SQL NULL observations column. Those must come back
     * exactly as they do today. constructObservations is annotated @NotNull and dereferences its argument
     * immediately, so the null has to be caught before it gets there or the endpoint 500s on old data.
     */
    @Test
    public void aDecisionWithNoAnswersReturnsNullAndNeverReachesObservationService() {
        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(null), "entity-uuid", observationService);

        assertTrue("the key should still be present, with an explicit null",
                response.containsKey("observations"));
        assertNull("a decision with no answers must not invent an empty list", response.get("observations"));
        verify(observationService, never()).constructObservations(any());
    }

    @Test
    public void aDecisionWithAnEmptyAnswerCollectionIsRenderedAsEmpty() {
        ObservationCollection observations = new ObservationCollection();
        when(observationService.constructObservations(observations)).thenReturn(Collections.emptyList());

        EntityApprovalStatusResponse response = EntityApprovalStatusResponse.fromEntityApprovalStatus(
                rejectionWith(observations), "entity-uuid", observationService);

        assertEquals(Collections.emptyList(), response.get("observations"));
    }
}
