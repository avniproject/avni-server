package org.avni.server.service.abdm.hip;

import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abha.AbhaEventRequest;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbdmVerifiedAbhaAspect aspect = new AbdmVerifiedAbhaAspect(access, queue, store);

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

    // The phone posts a corrected ABHA's record before the corrected form syncs: the save that brings the corrected ABHA
    // changes the patient's ABHA link, and their waiting visits are queued again if that ABHA is verified (AbdmVisitQueue
    // checks after the save).
    @Test
    public void aSaveThatChangedTheAbhaLinkQueuesTheWaitingVisitsAgainIfItsAbhaIsVerified() {
        when(access.recordSharingOn()).thenReturn(true);
        Individual subject = subject();

        aspect.abhaLinkKept(subject, true);

        verify(queue).requeueIfAbhaVerified(subject);
        verify(store, never()).hasVisitsWaitingForVerifiedAbha(anyLong());
    }

    // Most saves of a patient leave their ABHA as it was and have nothing waiting: no transaction of its own, no write.
    @Test
    public void aSaveThatLeftTheAbhaLinkAsItWasWithNothingWaitingDoesNothing() {
        when(access.recordSharingOn()).thenReturn(true);
        when(store.hasVisitsWaitingForVerifiedAbha(7L)).thenReturn(false);

        aspect.abhaLinkKept(subject(), false);

        verifyNoInteractions(queue);
    }

    @Test
    public void aSaveThatLeftTheAbhaLinkAsItWasStillQueuesVisitsWaitingForAVerifiedAbha() {
        when(access.recordSharingOn()).thenReturn(true);
        when(store.hasVisitsWaitingForVerifiedAbha(7L)).thenReturn(true);
        Individual subject = subject();

        aspect.abhaLinkKept(subject, false);

        verify(queue).requeueIfAbhaVerified(subject);
    }

    @Test
    public void withRecordSharingOffASavedSubjectQueuesNothing() {
        when(access.recordSharingOn()).thenReturn(false);

        aspect.abhaLinkKept(subject(), true);
        aspect.abhaLinkKept(subject(), false);
        aspect.abhaLinkKept(null, true);

        verifyNoInteractions(queue, store);
    }

    private static Individual subject() {
        Individual subject = new Individual();
        subject.setId(7L);
        subject.setUuid("subject-1");
        return subject;
    }

    private static AbhaEventRequest request(String ekaOid) {
        AbhaEventRequest request = new AbhaEventRequest();
        request.setSubjectUUID("subject-1");
        request.setEkaOid(ekaOid);
        return request;
    }
}
