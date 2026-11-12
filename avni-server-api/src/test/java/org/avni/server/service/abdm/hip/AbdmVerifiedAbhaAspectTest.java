package org.avni.server.service.abdm.hip;

import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abha.AbhaEventRequest;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// When the phone saves a record of a patient's ABHA with an Eka ID, the patient's visits waiting for a verified ABHA are
// queued again, only where record sharing is on. AbhaEventIntegrationTest covers the hook on the real endpoint.
public class AbdmVerifiedAbhaAspectTest {
    private final AbdmAccess access = mock(AbdmAccess.class);
    private final AbdmVisitQueue queue = mock(AbdmVisitQueue.class);
    private final AbdmVerifiedAbhaAspect aspect = new AbdmVerifiedAbhaAspect(access, queue);

    @Test
    public void aRecordWithAnEkaIdQueuesThePatientsWaitingVisitsAgain() {
        when(access.recordSharingOn()).thenReturn(true);

        aspect.requeueVisitsWaitingForIt(request("oid-1"), true);

        verify(queue).requeueForVerifiedAbha("subject-1");
    }

    @Test
    public void withRecordSharingOffNothingIsQueued() {
        when(access.recordSharingOn()).thenReturn(false);

        aspect.requeueVisitsWaitingForIt(request("oid-1"), true);

        verifyNoInteractions(queue);
    }

    // A record with no Eka ID verifies nothing; one stored before (a redelivery) has queued them already.
    @Test
    public void aRecordWithNoEkaIdOrStoredBeforeQueuesNothing() {
        when(access.recordSharingOn()).thenReturn(true);

        aspect.requeueVisitsWaitingForIt(request(null), true);
        aspect.requeueVisitsWaitingForIt(request("oid-1"), false);

        verify(queue, never()).requeueForVerifiedAbha(any());
        verifyNoInteractions(access);
    }

    private static AbhaEventRequest request(String ekaOid) {
        AbhaEventRequest request = new AbhaEventRequest();
        request.setSubjectUUID("subject-1");
        request.setEkaOid(ekaOid);
        return request;
    }
}
